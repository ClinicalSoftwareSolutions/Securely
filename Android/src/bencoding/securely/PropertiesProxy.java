/**
 * Securely Titanium Security Project
 * Copyright (c) 2009-2013 by Benjamin Bahrenburg. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 *
 */
package bencoding.securely;


import java.util.HashMap;
import java.util.List;

import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollPropertyChange;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollProxyListener;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiLifecycle;
import org.appcelerator.titanium.util.TiConvert;

import android.app.Activity;


@Kroll.proxy(creatableInModule=SecurelyModule.class)
public class PropertiesProxy  extends KrollProxy implements TiLifecycle.OnLifecycleEvent, KrollProxyListener 
{
	private String _secret = "";
	private Properties _appProperties;
	private static Boolean _encryptFieldNames = false;
	private static String _changedEventName = "changed";
	
	private String buildName(String name){
		return SecurelyModule.SECURELY_MODULE_FULL_NAME + "_" + name;
	};
	public PropertiesProxy()
	{
		super();		
		_appProperties = new Properties(TiApplication.getInstance().getApplicationContext(),buildName(TiApplication.getInstance().getAppInfo().getId()),false);
		_secret = TiApplication.getInstance().getAppGUID();
	}

	private String keyEncrypt(String key){
		if(_encryptFieldNames){
			String composed =  SHA.sha256(key);
			return ((composed == null)? key : composed);			
		}else{
			return key;
		}

	};
	private boolean keyExists(String key){
		if(_encryptFieldNames){
			return _appProperties.hasProperty(keyEncrypt(key));
		}else{
			return _appProperties.hasProperty(key);
		}		
	}
	
	private String ComposeSecret(String key){
		String Seed = _secret + "_" + key;
		String composed =  SHA.sha256(Seed);
		return ((composed == null)? Seed : composed);
	};
	private String EncryptContent(String PassKey, String value){
		try {
			String EncryptedText =  AESCrypto.encrypt(PassKey, value);
			return EncryptedText;
		} catch (Exception e) {
			e.printStackTrace();
			LogHelpers.Log(e);
			return null;
		}
	}
	private String DecryptContent(String PassKey, String value){
		try {
			String ClearText =  AESCrypto.decrypt(PassKey, value);
			return ClearText;
		} catch (Exception e) {
			e.printStackTrace();
			LogHelpers.Log(e);
			return null;
		}
	}	

	private void fireChanged(String propertyName, String actionType){
        if (hasListeners(_changedEventName)) {
            HashMap<String, Object> event = new HashMap<String, Object>();
            event.put("propertyName",propertyName);
            event.put("actionType",actionType);	            
            fireEvent(_changedEventName, event);
        }else{
        	LogHelpers.DebugLog("[DEBUG] no changed listener defined");
        }
	}
	@Override
	public void handleCreationDict(KrollDict options)
	{
		super.handleCreationDict(options);
		if (options.containsKey("identifier")) {
			String identifier = TiConvert.toString(options.get("identifier"));
			_appProperties = new Properties(TiApplication.getInstance().getApplicationContext(),buildName(identifier),false);
			LogHelpers.Level2Log("Setting identifer to : " + identifier);			
		}
		if (options.containsKey("secret")) {
			_secret = TiConvert.toString(options.get("secret"));
			LogHelpers.Level2Log("Setting secret to : " + _secret);		
		}	
		if (options.containsKey("encryptFieldNames")) {
			_encryptFieldNames = TiConvert.toBoolean(options.get("encryptFieldNames"));
			LogHelpers.Level2Log("Setting encrypt Fields to : " + _encryptFieldNames.toString());		
		}			
	}
	
	@Kroll.method
	public void setSecret(String value){
		_secret = value;
		LogHelpers.DebugLog("Setting secret to : " + _secret);			
	}
	
	@Kroll.method
	public void setIdentifier(String key){
		_appProperties = new Properties(TiApplication.getInstance().getApplicationContext(),key,false);
		LogHelpers.Level2Log("Setting identifer to : " + key);		
	}
	@Kroll.method
	public boolean getBool(String key,@Kroll.argument(optional=true) Object defaultValue )
	{
		Boolean ifMissingValue = false;
		if(!keyExists(key)){
			if(defaultValue != null){
				ifMissingValue = TiConvert.toBoolean(defaultValue);
			}
			return ifMissingValue;
		}
		String DecryptedStored = getString(key,ifMissingValue);
		return Converters.StringToBoolean(DecryptedStored);
	}

	@Kroll.method
	public double getDouble(String key,@Kroll.argument(optional=true) Object defaultValue )
	{
		double ifMissingValue = 0D;
		if(!keyExists(key)){
			if(defaultValue != null){
				ifMissingValue = TiConvert.toDouble(defaultValue);
			}		
			return ifMissingValue;
		}
		
		String DecryptedStored = getString(key,ifMissingValue);
		return Converters.StringToDouble(DecryptedStored);
	}

	@Kroll.method
	public int getInt(String key,@Kroll.argument(optional=true) Object defaultValue)
	{
		int ifMissingValue = 0;
		if(!keyExists(key)){
			if(defaultValue != null){
				ifMissingValue = TiConvert.toInt(defaultValue);
			}			
			return ifMissingValue;
		}		
		
		String DecryptedStored = getString(key,ifMissingValue);		
		return Converters.StringToInt(DecryptedStored);
	}

	@Kroll.method
	public boolean hasProperty(String key)
	{
		return keyExists(key);
	}
	
	@Kroll.method
	public boolean hasFieldsEncrypted()
	{
		return _encryptFieldNames;
	}

	@Kroll.method
	public String[] listProperties()
	{
		if(_encryptFieldNames){
			LogHelpers.info("Field names are encrypted and will not be returned");
			return null;
		}else{
			return _appProperties.listProperties();
		}
	}

	@Kroll.method
	public void removeProperty(String key)
	{
		if (keyExists(key)) {
			_appProperties.removeProperty(keyEncrypt(key));
			fireChanged(key,"removed");
		}
	}

	//Convenience method for pulling raw values
	public Object getPreferenceValue(String key)
	{
		return _appProperties.getPreference().getAll().get(key);
	}

	@Kroll.method
	public void setBool(String key, boolean value)
	{
		Object boolValue = getPreferenceValue(keyEncrypt(key));
		if (boolValue == null || !boolValue.equals(value)) {
			String ValueAsString = Converters.BooleanToString(value);
			String tempS = ComposeSecret(key);
			String EncryptedValue = EncryptContent(tempS,ValueAsString);
			_appProperties.setString(keyEncrypt(key), EncryptedValue);
			fireChanged(key,"modify");
		}
	}

	@Kroll.method
	public void setDouble(String key, double value)
	{
		Object doubleValue = getPreferenceValue(keyEncrypt(key));
		//Since there is no double type in SharedPreferences, we store doubles as strings, i.e "10.0"
		//so we need to convert before comparing.
		if (doubleValue == null || !doubleValue.equals(String.valueOf(value))) {
			String ValueAsString = Converters.DoubleToString(value);
			String tempS = ComposeSecret(key);
			String EncryptedValue = EncryptContent(tempS,ValueAsString);					
			_appProperties.setString(keyEncrypt(key), EncryptedValue);
			fireChanged(key,"modify");
		}
	}

	@Kroll.method
	public void setInt(String key, int value)
	{
		Object intValue = getPreferenceValue(keyEncrypt(key));
		if (intValue == null || !intValue.equals(value)) {
			String ValueAsString = Converters.IntToString(value);
			String tempS = ComposeSecret(key);
			String EncryptedValue = EncryptContent(tempS,ValueAsString);					
			_appProperties.setString(keyEncrypt(key), EncryptedValue);
			fireChanged(key,"modify");
		}

	}

	@Kroll.method
	public void setString(String key, String value)
	{
		Object stringValue = getPreferenceValue(keyEncrypt(key));
		if (stringValue == null || !stringValue.equals(value)) {
			String ValueAsString = TiConvert.toString(value);
			LogHelpers.Level2Log("setString key:" + key + " value:" + ValueAsString);
			
			String PassKey = ComposeSecret(key);
			LogHelpers.Level2Log("setString PassKey:" + PassKey);
			
			String EncryptedValue = EncryptContent(PassKey,ValueAsString);	
			LogHelpers.Level2Log("setString EncryptedValue:" + EncryptedValue);

			_appProperties.setString(keyEncrypt(key), EncryptedValue);
			fireChanged(key,"modify");
		}else{
			LogHelpers.Level2Log("setString not value to update. Key:" + key + " value:" + value);
		}
	}

	@Kroll.method
	public String getString(String key,@Kroll.argument(optional=true) Object defaultValue)
	{
		String ifMissingValue = null;
		if(!keyExists(key)){
			if(defaultValue != null){
				ifMissingValue = TiConvert.toString(defaultValue);
			}	
			
			return ifMissingValue;
		}
		String StoredValue = _appProperties.getString(keyEncrypt(key), ifMissingValue);
		LogHelpers.Level2Log("getString key:" + key + " value:" + StoredValue);
		String PassKey = ComposeSecret(key);
		LogHelpers.Level2Log("getString PassKey:" + PassKey);
		String TextValue = DecryptContent(PassKey,StoredValue);
		return TextValue;
	}
	@Kroll.method
	public void setObject(String key, @SuppressWarnings("rawtypes") HashMap value) 
	{
		if(value == null){
			setString(key,null);
			return;
		}
			
		try {
			String serializedString = Converters.serializeObjectToString(value);
	        LogHelpers.Level2Log("setObject serialized : " + serializedString);	
			setString(key,serializedString);
		} catch (Exception e) {
			e.printStackTrace();
			LogHelpers.Log(e);
		}
	}

	@SuppressWarnings("rawtypes")
	@Kroll.method
	public HashMap getObject(String key, @Kroll.argument(optional=true) HashMap defaultValue)
	{	
		if(!keyExists(key)){
			LogHelpers.DebugLog("getObject no properties found returning default");	
			return defaultValue;
		}else{
			//String temp = appProperties.getString(key,null);
			String temp = getString(key,null);
			LogHelpers.DebugLog("getObject string return : " + temp);	
			if(temp == null){
				return null;
			}
			
			try {
				LogHelpers.DebugLog("getObject Start deserialization ");	
				Object convertedObject = Converters.deserializeObjectFromString(temp);
				LogHelpers.DebugLog("getObject Finished deserialization ");	
				return (HashMap) convertedObject;
			} catch (Exception e) {
				e.printStackTrace();
				LogHelpers.Log(e);
				return null;
			}						
		}

	}
	@Kroll.method
	public void setList(String key, Object value)
	{
		if(value == null){
			setString(key,null);
			return;
		}
		if (!(value.getClass().isArray())) {
			throw new IllegalArgumentException("Argument must be an array");
		}

		try {
			String serializedString = Converters.serializeObjectToString(value);
	        LogHelpers.Level2Log("setList serialized : " + serializedString);	
			setString(key,serializedString);
		} catch (Exception e) {
			e.printStackTrace();
			LogHelpers.Log(e);
		}
	}


	@Kroll.method
	public Object[] getList(String key, @Kroll.argument(optional=true) Object defaultValue)
	{
		if(!keyExists(key)){
			if(defaultValue==null){
				LogHelpers.DebugLog("getList null value returned");	
				return null;
			}else{
				if (!(defaultValue.getClass().isArray())) {
					throw new IllegalArgumentException("Default value must be an array");
				}					
				return (Object[]) defaultValue;
			}			
		}else{

			String temp = getString(key,null);
			LogHelpers.Level2Log("getList string return : " + temp);	
			if(temp == null){
				return null;
			}
			
			try {
				LogHelpers.Level2Log("getList Start deserialization ");	
				Object convertedObject = Converters.deserializeObjectFromString(temp);
				LogHelpers.Level2Log("getList Finished deserialization ");	
				return (Object[]) convertedObject;
			} catch (Exception e) {
				e.printStackTrace();
				LogHelpers.Log(e);
				return null;
			}			
		}
	}	
	@Kroll.method
	public void setAccessGroup(String key){
		LogHelpers.DebugLog("setAccessGroup is not used on Android, method is available for parity sake only");		
	}
	@Kroll.method
	public void removeAllProperties(){
		_appProperties.getPreference().edit().clear().commit();
	}
	
    
	@Override
	public void onDestroy(Activity arg0) {
		if(_appProperties!=null){
			_appProperties.getPreference().edit().commit();
			_appProperties = null;
		}	
	}
	@Override
	public void onPause(Activity arg0) {}
	@Override
	public void onResume(Activity arg0) {}
	@Override
	public void onStart(Activity arg0) {}
	@Override
	public void onStop(Activity arg0) {}
	
    @Override
    public void listenerAdded(String type, int count, KrollProxy proxy) {}
    @Override
    public void listenerRemoved(String type, int count, KrollProxy proxy) {}
    @Override
	public void processProperties(KrollDict arg0) {}
	@Override
	public void propertiesChanged(List<KrollPropertyChange> arg0,KrollProxy arg1) {}
	@Override
	public void propertyChanged(String arg0, Object arg1, Object arg2,KrollProxy arg3) {}
}
