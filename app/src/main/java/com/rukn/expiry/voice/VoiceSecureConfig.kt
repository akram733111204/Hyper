package com.rukn.expiry.voice

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores only the owner-deployed proxy access token, never OpenAI API credentials.
 * Token is not exported in XLSX backups or application logs.
 */
object VoiceSecureConfig {
    private const val keyName="rukn_voice_proxy_token_aes"
    private const val preference="rukn_voice_private"
    private fun getKey():SecretKey{
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(keyName,null) as? SecretKey)?.let{return it}
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(keyName,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
    fun setToken(context:Context,token:String){
        val prefs=context.getSharedPreferences(preference,Context.MODE_PRIVATE)
        if(token.isBlank()){prefs.edit().remove("blob").apply();return}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,getKey())
        val bytes=cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("blob",Base64.encodeToString(cipher.iv+bytes,Base64.NO_WRAP)).apply()
    }
    fun getToken(context:Context):String=runCatching{
        val raw=context.getSharedPreferences(preference,Context.MODE_PRIVATE).getString("blob",null)?:return@runCatching ""
        val bytes=Base64.decode(raw,Base64.DEFAULT)
        require(bytes.size>13)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,getKey(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
    }.getOrDefault("")
}
