package dev.stillframe.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Remote-friendly, per-app icon selection; keeps browsing separate from applying. */
final class IconAppearancePicker {
    interface Save { void apply(String value); }
    private final Activity activity;
    private final IconPackSupport packs;
    private AlertDialog dialog;
    IconAppearancePicker(Activity a, IconPackSupport p) { activity=a; packs=p; }
    void dismiss() { if(dialog!=null) { dialog.dismiss(); dialog=null; } }
    private int dp(int n) { return Math.round(n*activity.getResources().getDisplayMetrics().density); }

    void show(String appName, Save save) {
        dismiss();
        JSONArray found=packs.discover();
        String[] labels=new String[found.length()+2];
        labels[0]="Automatic — follow selected icon pack";
        labels[1]="Original app icon";
        for(int i=0;i<found.length();i++) labels[i+2]=found.optJSONObject(i).optString("name");
        dialog=new AlertDialog.Builder(activity).setTitle("Change appearance — "+appName)
            .setItems(labels,(d,index)->{
                if(index==0) { save.apply("");return; }
                if(index==1) { save.apply("{\"original\":true}");return; }
                JSONObject pack=found.optJSONObject(index-2);
                showIcons(pack.optString("id"),pack.optString("name"),appName,save);
            }).setNegativeButton("Cancel",null).create();
        dialog.show();
    }

    private void showIcons(String pack, String title, String app, Save save) {
        dismiss();
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16),dp(4),dp(16),0);
        EditText search=new EditText(activity);search.setSingleLine(true);search.setHint("Search icons");
        body.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView status=new TextView(activity);status.setPadding(0,dp(8),0,dp(8));status.setText("Loading icons…");
        body.addView(status);
        GridView grid=new GridView(activity);grid.setNumColumns(6);grid.setVerticalSpacing(dp(8));grid.setHorizontalSpacing(dp(8));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        body.addView(grid,new LinearLayout.LayoutParams(-1,dp(310)));
        AlertDialog current=new AlertDialog.Builder(activity).setTitle(title+" — "+app)
            .setView(body).setNegativeButton("Back",(d,w)->show(app,save)).create();
        dialog=current;current.show();
        if(current.getWindow()!=null) current.getWindow().setLayout(dp(700),-2);
        new Thread(()->{
            List<String> names=packs.listIcons(pack);
            activity.runOnUiThread(()->{
                if(activity.isDestroyed()||dialog!=current||!current.isShowing())return;
                List<String> visible=new ArrayList<>(names);
                BaseAdapter adapter=new BaseAdapter(){
                    public int getCount(){return visible.size();}
                    public Object getItem(int i){return visible.get(i);}
                    public long getItemId(int i){return i;}
                    public View getView(int i,View recycled,ViewGroup parent){
                        LinearLayout tile;
                        if(recycled instanceof LinearLayout) tile=(LinearLayout)recycled;
                        else {
                            tile=new LinearLayout(activity);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(17);
                            tile.setPadding(dp(5),dp(5),dp(5),dp(5));
                            tile.setBackgroundResource(android.R.drawable.list_selector_background);
                            ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            tile.addView(image,new LinearLayout.LayoutParams(dp(60),dp(60)));
                            TextView text=new TextView(activity);text.setTextColor(Color.WHITE);text.setTextSize(11);text.setMaxLines(2);text.setGravity(17);
                            tile.addView(text,new LinearLayout.LayoutParams(-1,dp(34)));
                        }
                        String name=visible.get(i);
                        ((ImageView)tile.getChildAt(0)).setImageDrawable(packs.namedIcon(pack,name));
                        ((TextView)tile.getChildAt(1)).setText(name.replace('_',' '));
                        tile.setContentDescription(name.replace('_',' '));
                        return tile;
                    }
                };
                grid.setAdapter(adapter);status.setText(names.size()+" icons · OK applies · Back cancels");
                grid.setOnItemClickListener((parent,v,index,id)->{
                    try {
                        String name=visible.get(index);
                        if(packs.namedIcon(pack,name)==null) {Toast.makeText(activity,"Icon unavailable",Toast.LENGTH_SHORT).show();return;}
                        save.apply(new JSONObject().put("pack",pack).put("drawable",name).toString());dismiss();
                    }catch(Exception ignored){}
                });
                search.addTextChangedListener(new TextWatcher(){
                    public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                    public void onTextChanged(CharSequence s,int start,int before,int count){
                        String query=s.toString().trim().toLowerCase(Locale.ROOT).replace(' ','_');visible.clear();
                        for(String name:names)if(name.toLowerCase(Locale.ROOT).contains(query))visible.add(name);
                        adapter.notifyDataSetChanged();status.setText(visible.size()+" / "+names.size()+" icons");grid.setSelection(0);
                    }
                    public void afterTextChanged(Editable e){}
                });
                if(!visible.isEmpty())grid.requestFocus();
                else status.setText("No readable icons found in this pack");
            });
        },"StillFrameIconCatalog").start();
    }
}
