package org.lakeman.multi_userappinstall;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class MainActivity extends Activity implements OnItemClickListener {

	private PackageManager pm;
	private File index;
	private static final String TAG="MultiUserInstall";
	private List<App> appList;
	private ListView listView;
	private ArrayAdapter<App> adapter;
	
	private class App{
		final String packageName;
		final CharSequence appName;
		final File sourceDir;
		final boolean installed;
		boolean shared=false;
		
		public App(ApplicationInfo info, boolean installed){
			this.packageName = info.packageName;
			this.appName = info.loadLabel(pm);
			this.sourceDir = new File(info.sourceDir);
			this.installed = installed;
		}
		
		@Override
		public boolean equals(Object o) {
			if (!(o instanceof App))
				return false;
			return ((App)o).sourceDir.equals(sourceDir);
		}
		
		@Override
		public int hashCode() {
			return sourceDir.hashCode();
		}

		@Override
		public String toString() {
			return appName+(shared?" Shared":"")+(installed?" Installed":"");
		}
	}
	
	private ApplicationInfo getApkInfo(String apk){
		PackageInfo info = pm.getPackageArchiveInfo(apk, 0);
		// see http://code.google.com/p/android/issues/detail?id=9151
		info.applicationInfo.sourceDir=apk;
		info.applicationInfo.publicSourceDir=apk;
		return info.applicationInfo;
	}
	
	private void save() throws FileNotFoundException, IOException, InterruptedException{
		Properties p=new Properties();
		for (App a:appList){
			if (a.shared)
				p.setProperty(a.packageName, a.sourceDir.getAbsolutePath());
		}
		index.getParentFile().mkdirs();
		FileOutputStream out = new FileOutputStream(index);
		p.store(out, "");
		out.close();
		run("chmod 755 "+index.getParentFile().getAbsolutePath());
		run("chmod 777 "+index.getAbsolutePath());
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		try{
			List<ApplicationInfo> packages = pm.getInstalledApplications(0);
			Map<String, App> apps = new HashMap<String, App>();
			appList = new ArrayList<App>();
			
			for (int i=0;i<packages.size();i++){
				ApplicationInfo info = packages.get(i);
				if ((info.flags & ApplicationInfo.FLAG_SYSTEM)!=0)
					continue;
				Log.v(TAG, "Installed; "+info.packageName+" - "+info.sourceDir);
				App a = new App(info, true);
				apps.put(info.packageName, a);
				appList.add(a);
			}
			
			try{
				Properties p=new Properties();
				FileInputStream in = new FileInputStream(index);
				p.load(in);
				in.close();
				
				for (Entry<Object, Object> entry:p.entrySet()){
					String packageName = entry.getKey().toString();
					String sourceDir = entry.getValue().toString();
					App a = apps.get(packageName);
					if (a==null){
						a = new App(getApkInfo(sourceDir), false);
						apps.put(packageName, a);
						appList.add(a);
					}
					Log.v(TAG, "Shared; "+a.packageName+" - "+a.sourceDir);
					a.shared=true;
				}
			}catch (Exception e){
				Log.v(TAG, e.getMessage(), e);
			}
			adapter=new ArrayAdapter<App>(this, android.R.layout.simple_expandable_list_item_1, appList);
			listView.setAdapter(adapter);

		}catch (Exception e){
			Log.e(TAG,e.getMessage(),e);
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		try {
			this.save();
		} catch (Exception e) {
			Log.e(TAG,e.getMessage(),e);
		}
	}

	private int run(String cmd) throws IOException, InterruptedException{
		Process proc = new ProcessBuilder("/system/bin/sh")
				.redirectErrorStream(true)
				.start();
		OutputStream out = proc.getOutputStream();
		out.write((cmd+"\n").getBytes());
		out.flush();
		out.close();
		return proc.waitFor();
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		try{
			
			listView = (ListView)this.findViewById(R.id.listView);
			listView.setOnItemClickListener(this);
			pm = this.getPackageManager();
			index=new File("/data/data/org.lakeman.multi_userappinstall/files/shared.txt");
			
		} catch (Exception e) {
			Log.e(TAG,e.getMessage(),e);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		App app = (App)listView.getItemAtPosition(position);
		if (app.installed){
			app.shared = !app.shared;
			adapter.notifyDataSetChanged();
		}else{
			// install!
			Intent i = new Intent("android.intent.action.VIEW")
			.setType("application/vnd.android.package-archive")
			.setClassName("com.android.packageinstaller",
					"com.android.packageinstaller.PackageInstallerActivity")
			.setData(Uri.fromFile(app.sourceDir));
			this.startActivity(i);
		}
	}

}
