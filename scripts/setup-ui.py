"""Desktop setup for Northland Thor. Requires Python with Tk and Android ADB."""
import argparse
from pathlib import Path
import queue
import threading
import tkinter as tk
from tkinter import filedialog,ttk
import webbrowser
from setup_backend import ROOT,default_adb,find_devices,perform


class SetupWindow:
    def __init__(self,root,adb=None,artifacts=None):
        self.root=root;self.devices=[];self.events=queue.Queue();self.busy=False
        root.title('Northland Thor setup');root.geometry('850x650');root.minsize(720,560)
        self.adb=tk.StringVar(value=adb or default_adb())
        self.artifacts=tk.StringVar(value=str(artifacts or ROOT/'artifacts/android'))
        self.device=tk.StringVar();self.status=tk.StringVar(value='Connect the Thor by USB, then find devices.')
        self.frame=ttk.Frame(root,padding=20);self.frame.pack(fill='both',expand=True)
        self.frame.columnconfigure(1,weight=1);self.frame.rowconfigure(8,weight=1)
        ttk.Label(self.frame,text='Northland on AYN Thor',font=('',20,'bold')).grid(row=0,column=0,columnspan=3,sticky='w')
        ttk.Label(self.frame,text='Install our companion and activate its controls. Your purchased game and saves stay in place.',
                  wraplength=780).grid(row=1,column=0,columnspan=3,sticky='w',pady=(5,20))
        self.fields=[]
        for row,label,var,browse in [(2,'ADB',self.adb,self.choose_adb),(3,'Build folder',self.artifacts,self.choose_build)]:
            ttk.Label(self.frame,text=label).grid(row=row,column=0,sticky='w',padx=(0,12),pady=5)
            entry=ttk.Entry(self.frame,textvariable=var);entry.grid(row=row,column=1,sticky='ew',pady=5)
            button=ttk.Button(self.frame,text='Browse…',command=browse);button.grid(row=row,column=2,padx=(8,0),pady=5)
            self.fields.extend([entry,button])
        ttk.Label(self.frame,text='Thor').grid(row=4,column=0,sticky='w')
        self.device_box=ttk.Combobox(self.frame,textvariable=self.device,state='readonly')
        self.device_box.grid(row=4,column=1,sticky='ew',pady=5)
        self.refresh_button=ttk.Button(self.frame,text='Find devices',command=self.refresh)
        self.refresh_button.grid(row=4,column=2,padx=(8,0))
        actions=ttk.Frame(self.frame);actions.grid(row=5,column=0,columnspan=3,sticky='w',pady=15)
        self.buttons={}
        for label,action in [('Install / update + activate','setup'),('Activate installed app','activate'),
                             ('Open game','game'),('Stop controls','stop')]:
            button=ttk.Button(actions,text=label,command=lambda a=action:self.action(a))
            button.pack(side='left',padx=(0,8));self.buttons[action]=button
        ttk.Label(self.frame,textvariable=self.status,wraplength=780).grid(row=6,column=0,columnspan=3,sticky='w',pady=5)
        self.progress=ttk.Progressbar(self.frame,mode='indeterminate')
        self.progress.grid(row=7,column=0,columnspan=3,sticky='ew',pady=5)
        self.log=tk.Text(self.frame,height=12,wrap='word',state='disabled')
        self.log.grid(row=8,column=0,columnspan=3,sticky='nsew',pady=5)
        links=ttk.Frame(self.frame);links.grid(row=9,column=0,columnspan=3,sticky='w',pady=(10,0))
        ttk.Button(links,text='Get Android Platform Tools',command=lambda:webbrowser.open(
            'https://developer.android.com/tools/releases/platform-tools')).pack(side='left')
        ttk.Button(links,text='On-device activation guide',command=lambda:webbrowser.open(
            'https://github.com/ROM-HackZ/Cultures-Northland-AYN-Thor/blob/main/docs/setup.md')).pack(side='left',padx=8)
        root.after(60,self.poll)

    def choose_adb(self):
        path=filedialog.askopenfilename(title='Choose adb from Android Platform Tools')
        if path:self.adb.set(path)

    def choose_build(self):
        path=filedialog.askdirectory(title='Folder containing northland-panel.apk and northland-bridge.jar')
        if path:self.artifacts.set(path)

    def append(self,text):
        self.log.configure(state='normal');self.log.insert('end',text+'\n');self.log.see('end');self.log.configure(state='disabled')

    def run(self,label,operation):
        if self.busy:return
        self.busy=True;self.status.set(label);self.progress.start(12)
        for button in [*self.buttons.values(),self.refresh_button,*self.fields]:button.configure(state='disabled')
        self.device_box.configure(state='disabled')
        def worker():
            try:operation();self.events.put(('done',None))
            except Exception as error:self.events.put(('error',str(error)))
        threading.Thread(target=worker,daemon=True).start()

    def refresh(self):
        adb=self.adb.get().strip()
        self.run('Looking for the Thor…',lambda:self.events.put(('devices',find_devices(adb,lambda text:self.events.put(('log',text))))))

    def action(self,action):
        index=self.device_box.current()
        if not 0<=index<len(self.devices):self.status.set('Find devices and choose your Thor first.');return
        adb=self.adb.get().strip();artifacts=self.artifacts.get().strip();device=self.devices[index]
        self.run({'setup':'Installing and activating…','activate':'Activating controls…','game':'Opening Northland…','stop':'Stopping controls…'}[action],
                 lambda:perform(adb,device,artifacts,action,lambda text:self.events.put(('log',text))))

    def poll(self):
        try:
            while True:
                kind,value=self.events.get_nowait()
                if kind=='log':self.append(value)
                elif kind=='devices':
                    self.devices=value
                    self.device_box['values']=[f'{d.serial} — {d.state}  {d.description}' for d in value]
                    if len(value)==1:self.device_box.current(0)
                    else:self.device_box.set('')
                    if not value:self.status.set('No devices found. Enable USB debugging and reconnect the Thor.')
                    elif len(value)==1 and value[0].state=='unauthorized':self.status.set('Unlock the Thor and allow USB debugging, then find devices again.')
                    else:self.status.set('Choose your Thor, then install or activate.')
                else:
                    self.busy=False;self.progress.stop()
                    for button in [*self.buttons.values(),self.refresh_button,*self.fields]:button.configure(state='normal')
                    self.device_box.configure(state='readonly')
                    if kind=='error':self.status.set(value);self.append(value)
                    elif not self.status.get().startswith(('Choose','No devices','Unlock')):
                        self.status.set('Done. Open Northland and release the sticks and buttons.')
        except queue.Empty:pass
        self.root.after(60,self.poll)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb');parser.add_argument('--artifacts',type=Path)
    args=parser.parse_args();root=tk.Tk();SetupWindow(root,args.adb,args.artifacts);root.mainloop()


if __name__=='__main__':main()
