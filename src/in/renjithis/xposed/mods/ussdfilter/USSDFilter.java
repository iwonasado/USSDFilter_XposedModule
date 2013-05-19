package in.renjithis.xposed.mods.ussdfilter;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;
import android.app.Notification;
import android.app.NotificationManager;

// Imports for XposedBridge
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

//Imports for PhoneUtils class
import android.content.Context;

public class USSDFilter implements IXposedHookLoadPackage {

	private String TAG="USSDFilter";
	
	private void myLog(String logString) {
		XposedBridge.log(TAG + ": " + logString);
	}
	
	public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
		//        XposedBridge.log("Loaded app: " + lpparam.packageName);

		if(!lpparam.packageName.equals("com.android.phone"))
			return;

		myLog("Found phone app");

		findAndHookMethod("com.android.phone.PhoneUtils", 
				lpparam.classLoader, 
				"displayMMIComplete", 
				"com.android.internal.telephony.Phone",
				"android.content.Context",
				"com.android.internal.telephony.MmiCode",
				"android.os.Message",
				"android.app.AlertDialog",
				new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				// this will be called before the USSD message is displayed by the original method
				myLog("beforeHookedMethod displayMMIComplete");

				Context context = (Context) param.args[1];
				Object mmiCode = param.args[2];
				Method getMessageMethod = mmiCode.getClass().getDeclaredMethod("getMessage");
				Method isUssdRequestMethod = mmiCode.getClass().getDeclaredMethod("isUssdRequest");
		
				if((Boolean)isUssdRequestMethod.invoke(mmiCode))
				{
					myLog("USSD Request detected. Not filtering");
					return;
				}
					
				ArrayList<Filter> filterSettings = filterSettings();
				
				for (Filter filter : filterSettings) {
					if(!filter.enabled || filter.subStringRegEx == null) {
						myLog("Filter is not enabled. Filer name : " + filter.name);
						continue;
					}

					String mmiText = (String) getMessageMethod.invoke(mmiCode);
					myLog("mmiText="+mmiText);
					
					Boolean filterMatch = Boolean.FALSE;
					
					if(filter.type == FilterType.TYPE_ALL)
						filterMatch = Boolean.TRUE;
					else if(filter.type == FilterType.TYPE_SUBSTRING) {
						if(mmiText.contains(filter.subStringRegEx))
							filterMatch = Boolean.TRUE;
					}
					else if(filter.type == FilterType.TYPE_REGEX) {
//						myLog("RegEx matching not yet implemented");
                        if(mmiText.matches(filter.subStringRegEx))
                            filterMatch = Boolean.TRUE;
					}

                    String logString="";

                    if(filterMatch)
                    {
						// need to add more functionality, like logging, etc

						myLog("Text contains filterString");
						if(filter.outputType == OutputType.TYPE_TOAST)
                        {
							Toast.makeText(context, mmiText, Toast.LENGTH_LONG).show();
                            logString.concat("[Toast] ");
                        }
						else if(filter.outputType == OutputType.TYPE_NOTIFICATION)
                        {
							showNotification(context, "USSD Message Received", mmiText);
                            logString.concat("[Notification] ");
                        }
                        else
                        {
                            logString.concat("[Silent] ");
                        }
                        logString.concat(mmiText);
						// This prevents the actual hooked method from being called
						param.setResult(mmiCode);
					}
                    else
                    {
                        logString.concat("[Allowed] " + mmiText);
                    }

                    FileManagement.writeFileToExternalStorage("USSDFilter.log", logString, Boolean.TRUE);
				}
			}
		});
	}
	
	private ArrayList<Filter> filterSettings() {
		ArrayList<Filter> filterList = new ArrayList<Filter>();
		
		// dummy data - replace with DB calls **************************
		Filter filter = new Filter();
		filter.name = "Filter1";
		filter.type= FilterType.TYPE_SUBSTRING;
		filter.subStringRegEx = FileManagement.readFileFromExternalStorage("USSDFilterString.conf");
		filter.outputType = OutputType.TYPE_TOAST;
		filter.priority = 1;
		filter.enabled = Boolean.TRUE;
		
		filterList.add(filter);
		
		//**************************************************************
		
		
		return filterList;
	}

	private void showNotification(Context context, String title, String contentText) {
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
		        .setSmallIcon(R.drawable.ic_launcher)
		        .setContentTitle(title)
		        .setContentText(contentText);
		mBuilder.setAutoCancel(true);
		

		NotificationManager mNotificationManager =
		    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.notify(0, mBuilder.build());
	}
	 
}
