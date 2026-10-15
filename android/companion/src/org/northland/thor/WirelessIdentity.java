package org.northland.thor;

import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import android.util.Base64;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** ADB identity is private to this installation; its RSA key is encrypted at rest. */
final class WirelessIdentity {
    private static final String ALIAS="northland-wireless-key";
    final PrivateKey key;
    final X509Certificate certificate;
    private WirelessIdentity(PrivateKey key,X509Certificate certificate) { this.key=key;this.certificate=certificate; }
    static synchronized WirelessIdentity load(Context context) throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)) {
            KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());generator.generateKey();
        }
        SecretKey wrapping=(SecretKey)store.getKey(ALIAS,null);
        AtomicFile file=new AtomicFile(new File(context.getNoBackupFilesDir(),"wireless-identity.json"));
        if(file.getBaseFile().isFile()) {
            JSONObject data=new JSONObject(new String(file.readFully(),"UTF-8"));
            Cipher decrypt=Cipher.getInstance("AES/GCM/NoPadding");
            decrypt.init(Cipher.DECRYPT_MODE,wrapping,new GCMParameterSpec(128,decode(data.getString("iv"))));
            byte[] plain=decrypt.doFinal(decode(data.getString("key")));
            try {
                PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(plain));
                X509Certificate cert=(X509Certificate)CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(decode(data.getString("certificate"))));
                return new WirelessIdentity(key,cert);
            } finally { Arrays.fill(plain,(byte)0); }
        }
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
        java.security.KeyPair pair=generator.generateKeyPair();
        X500Name subject=new X500Name("CN=Northland Thor");long now=System.currentTimeMillis();
        byte[] certBytes=new JcaX509v3CertificateBuilder(subject,new BigInteger(128,new SecureRandom()),
            new Date(now-86400000L),new Date(now+10L*365*86400000L),subject,pair.getPublic())
            .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())).getEncoded();
        X509Certificate certificate=(X509Certificate)CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(certBytes));
        Cipher encrypt=Cipher.getInstance("AES/GCM/NoPadding");encrypt.init(Cipher.ENCRYPT_MODE,wrapping);
        byte[] plain=pair.getPrivate().getEncoded(),encrypted;
        try { encrypted=encrypt.doFinal(plain); } finally { Arrays.fill(plain,(byte)0); }
        JSONObject data=new JSONObject().put("iv",encode(encrypt.getIV())).put("key",encode(encrypted))
            .put("certificate",encode(certBytes));
        FileOutputStream output=file.startWrite();
        try { output.write(data.toString().getBytes("UTF-8"));file.finishWrite(output); }
        catch(Exception e) { file.failWrite(output);throw e; }
        return new WirelessIdentity(pair.getPrivate(),certificate);
    }
    private static String encode(byte[] value) { return Base64.encodeToString(value,Base64.NO_WRAP); }
    private static byte[] decode(String value) { return Base64.decode(value,Base64.NO_WRAP); }
}
