package com.juanvvc.comicviewer;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class FileExplorer extends ListActivity {

	private List<String> item = null;
	private List<String> path = null;
	private String root="/mnt/sdcard/";
	private TextView myPath;
	private String dirPath=root;
	private static final String SEP="/";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.filelist);
		myPath = (TextView)findViewById(R.id.path);
		getDir(root);
	}

	private void getDir(String dirPath)
	{
		myPath.setText("Location: " + dirPath);
		this.dirPath = dirPath;

		item = new ArrayList<String>();
		path = new ArrayList<String>();

		File f = new File(dirPath);
		File[] files = f.listFiles();

		if(!dirPath.equals(root))
		{

			item.add(root);
			path.add(root);

			item.add("../");
			path.add(f.getParent());

		}

		for(int i=0; i < files.length; i++)
		{
			File file = files[i];
			path.add(file.getPath());
			if(file.isDirectory())
				item.add(file.getName() + "/");
			else
				item.add(file.getName());
		}

		ArrayAdapter<String> fileList =
				new ArrayAdapter<String>(this, R.layout.filerow, item);
		setListAdapter(fileList);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {

		File file = new File(path.get(position));

		if (file.isDirectory()) {
			if(file.canRead())
				getDir(path.get(position));
			else {
				new AlertDialog.Builder(this)
				.setIcon(R.drawable.icon)
				.setTitle("[" + file.getName() + "] folder can't be read!")
				.setPositiveButton("OK", 
						new DialogInterface.OnClickListener() {

							public void onClick(DialogInterface dialog,	int which) {
								// TODO Auto-generated method stub
								
							}

				}).show();
			}
		} else {
//			new AlertDialog.Builder(this)
//			.setIcon(R.drawable.icon)
//			.setTitle("[" + file.getName() + "]")
//			.setPositiveButton("OK", 
//					new DialogInterface.OnClickListener() {
//
//						public void onClick(DialogInterface dialog, int which) {
//							// TODO Auto-generated method stub
//							
//						}
//
//			}).show();
			Intent data=new Intent();
			data.putExtra("file", this.dirPath+SEP+file.getName());
			setResult(RESULT_OK, data);
			finish();
		}
	}
}
