/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package actualizador;

import org.jasypt.util.text.AES256TextEncryptor;

/**
 *
 * @author omega
 */
public class Utilerias {
    
    
     public static String DecryptPassword(String passwordFromConfigFile) {           
        AES256TextEncryptor aesEncryptor = new AES256TextEncryptor();
        aesEncryptor.setPassword("pareidolia");
        String decryptedPassword = aesEncryptor.decrypt(passwordFromConfigFile);
        return decryptedPassword;   
    }
    
    
}
