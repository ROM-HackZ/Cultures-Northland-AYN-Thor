package org.northland.thor;

/** Known command names, shared by the panel and the authenticated input bridge. */
public enum VillagerAction {
    PROFESSION("change-profession","Change profession","profession"),
    EQUIPMENT("change-equipment","Change equipment",null),
    HOME("assign-home","Assign home","home"),
    LEARNING("assign-learning","Assign learning place",null),
    VEHICLE("assign-vehicle","Assign vehicle",null),
    GO_TO("go-to","Go to",null),
    MARRY("marry","Marry",null),
    PRAY("pray","Pray",null),
    TALK("talk","Talk",null),
    SLEEP("sleep","Sleep",null),
    EAT("eat","Eat",null),
    ATTACK_INHABITANTS("attack-inhabitants","Attack inhabitants",null),
    ATTACK_BUILDING("attack-building","Attack building",null),
    ATTACK_ANIMAL("attack-animal","Attack animal",null),
    ATTACK_VEHICLE("attack-vehicle","Attack vehicle",null),
    HAVE_A_BOY("have-a-boy","Have a boy",null),
    HAVE_A_GIRL("have-a-girl","Have a girl",null),
    REMOVE_HOME("remove-home","Remove home",null),
    REMOVE_VEHICLE("remove-vehicle","Remove vehicle",null),
    ASSIGN_WORK_PLACE("assign-work-place","Assign work place","workplace"),
    REMOVE_WORK_PLACE("remove-work-place","Remove work place",null),
    REMOVE_LEARNING_PLACE("remove-learning-place","Remove learning place",null),
    ASSIGN_BUILDING_SITE("assign-building-site","Assign building site",null),
    REMOVE_BUILDING_SITE("remove-building-site","Remove building site",null),
    ASSIGN_TRADE_POST("assign-trade-post","Assign trade post",null),
    REMOVE_TRADE_POST("remove-trade-post","Remove trade post",null),
    CHANGE_PRODUCTION("change-production","Change production",null),
    ASSIGN_WORK_AREA("assign-work-area","Assign work area",null),
    SHOW_WORK_AREA("show-work-area","Show work area",null),
    ERECT_SIGNPOST("erect-signpost","Erect signpost",null),
    EXPLORE("explore","Explore",null),
    SOLDIER_ATTACK_MODE("soldier-attack-mode","Soldier attack mode",null),
    SOLDIER_DEFENCE_MODE("soldier-defence-mode","Soldier defence mode",null),
    SOLDIER_IGNORANT_MODE("soldier-ignorant-mode","Soldier ignorant mode",null),
    SOLDIER_MODE_4("soldier-mode-4","Soldier mode 4",null),
    ALLOW_REGENERATION("allow-regeneration","Allow regeneration",null),
    PROHIBIT_REGENERATION("prohibit-regeneration","Prohibit regeneration",null),
    ATTACK_POSITION("attack-position","Attack position",null);

    public final String id,label,shortcut;
    VillagerAction(String id,String label,String shortcut) {
        this.id=id;this.label=label;this.shortcut=shortcut;
    }
    public static VillagerAction fromId(String id) {
        for(VillagerAction action:values()) if(action.id.equals(id)) return action;
        throw new IllegalArgumentException("Unknown villager action");
    }
    public static VillagerAction fromLabel(String label) {
        for(VillagerAction action:values()) if(action.label.equals(label)) return action;
        throw new IllegalArgumentException("Unknown villager action label");
    }
}
