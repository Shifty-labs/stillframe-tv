package dev.stillframe.tv;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.provider.OpenableColumns;
import android.provider.Settings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.UUID;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.LruCache;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** TV app with an opt-in HOME activity; selecting it never disables other launchers. */
public class MainActivity extends Activity {
    private static final int PICK_WALLPAPER = 42;
    private static final int MAX_IMPORTS = 20;
    private final Object importsLock = new Object();
    private WebView web;
    private WallpaperSources wallpaperSources;
    private ProtonVpnShortcut protonShortcut;
    private IconPackSupport iconPacks;
    private IconAppearancePicker iconPicker;
    private String iconPackId = "";
    private final Handler holdHandler = new Handler(Looper.getMainLooper());
    private boolean okHeld = false;
    private final Runnable holdAction = () -> {
        okHeld = true;
        sendRemoteKey("hold");
    };
    private void sendRemoteKey(String action) {
        if (web != null) web.evaluateJavascript("window.tvRemote('" + action + "')", null);
    }
    private void cancelPendingHold() { holdHandler.removeCallbacks(holdAction); }

    private final Map<String, Intent> launchTargets = new ConcurrentHashMap<>();
    private final Map<String, ResolveInfo> appEntries = new ConcurrentHashMap<>();
    private final LruCache<String, String> iconCache = new LruCache<>(60);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        iconPacks = new IconPackSupport(this);
        iconPicker = new IconAppearancePicker(this,iconPacks);
        iconPackId = getPreferences(MODE_PRIVATE).getString("iconPack", "");
        if (!iconPacks.select(iconPackId)) iconPackId = "";
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.getSettings().setAllowFileAccessFromFileURLs(false);
        web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.addJavascriptInterface(new Bridge(), "StillFrameAndroid");
        android.widget.FrameLayout root=new android.widget.FrameLayout(this);
        root.addView(web,new android.widget.FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        wallpaperSources=new WallpaperSources(this,root,web);
        protonShortcut=new ProtonVpnShortcut(this,root,accepted->{
            if(web!=null)web.evaluateJavascript("window.vpnQuickConnectResult && window.vpnQuickConnectResult("+JSONObject.quote(accepted)+")",null);
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // UI is entirely local. Refuse navigation to external or unrelated pages.
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if ("file:///android_asset/index.html".equals(url)) {sendBootstrap();wallpaperSources.publish();}
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    private final Handler networkHandler = new Handler(Looper.getMainLooper());
    private final Runnable networkTick = new Runnable() {
        @Override public void run() {
            publishNetworkStatus();
            networkHandler.postDelayed(this, 2000L);
        }
    };

    private void publishNetworkStatus() {
        if (web == null) return;
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            NetworkCapabilities active = cm.getNetworkCapabilities(cm.getActiveNetwork());
            boolean wifi = false, ethernet = false, vpn = false;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c == null) continue;
                vpn |= c.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                if (!c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    wifi |= c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                    ethernet |= c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
                }
            }
            JSONObject status = new JSONObject();
            status.put("wifi", wifi);
            status.put("ethernet", ethernet);
            status.put("vpn", vpn);
            status.put("wifiActive", active != null && active.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
            status.put("ethernetActive", active != null && active.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            web.evaluateJavascript("window.updateNetworkStatus && window.updateNetworkStatus(" + status + ")", null);
        } catch (Exception ignored) {
            web.evaluateJavascript("window.updateNetworkStatus && window.updateNetworkStatus(null)", null);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if(protonShortcut!=null)protonShortcut.resume();
        if(wallpaperSources!=null)wallpaperSources.resume();
        networkHandler.removeCallbacks(networkTick);
        networkHandler.post(networkTick);
        if (iconPacks != null) {
            if (!iconPacks.select(iconPackId)) iconPackId = "";
            synchronized(iconCache) { iconCache.evictAll(); }
            if (web != null) web.evaluateJavascript("window.refreshIconPacks && window.refreshIconPacks()",null);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            cancelPendingHold();
            iconPicker.dismiss();
            wallpaperSources.home();
            sendRemoteKey("home");
        }
    }

    private void collectLauncherActivities(PackageManager pm, String category, Map<String, ResolveInfo> result) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (getPackageName().equals(pkg)) continue;
            if (!result.containsKey(pkg)) result.put(pkg, info);
        }
    }

    private void sendBootstrap() {
        try {
            PackageManager pm = getPackageManager();
            Map<String, ResolveInfo> discovered = new LinkedHashMap<>();
            collectLauncherActivities(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, discovered);
            collectLauncherActivities(pm, Intent.CATEGORY_LAUNCHER, discovered);
            appEntries.clear();
            launchTargets.clear();
            List<JSONObject> items = new ArrayList<>();
            for (Map.Entry<String, ResolveInfo> entry : discovered.entrySet()) {
                String pkg = entry.getKey();
                ResolveInfo info = entry.getValue();
                Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
                if (launch == null) {
                    launch = new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(pkg, info.activityInfo.name);
                }
                launchTargets.put(pkg, launch);
                appEntries.put(pkg, info);
                JSONObject app = new JSONObject();
                app.put("id", pkg);
                CharSequence name = info.loadLabel(pm);
                app.put("name", name == null ? pkg : name.toString());
                // Informational label only. A pre-installed app may still be used daily.
                boolean preinstalled = false;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    preinstalled = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                } catch (PackageManager.NameNotFoundException ignored) { }
                app.put("installType", preinstalled ? "Pre-installed" : "Installed separately");
                items.add(app);
            }
            Collections.sort(items, Comparator.comparing(item -> item.optString("name").toLowerCase()));
            JSONArray apps = new JSONArray();
            for (JSONObject item : items) apps.put(item);
            JSONObject payload = new JSONObject();
            payload.put("apps", apps);
            payload.put("supportsVideoWallpapers",BuildConfig.HAS_VIDEO_WALLPAPERS);
            payload.put("importedWallpapers", getImportedMetadata());
            String saved = getPreferences(MODE_PRIVATE).getString("ui", "{}");
            try { payload.put("prefs", new JSONObject(saved)); }
            catch (JSONException ignored) { payload.put("prefs", new JSONObject()); }
            if (web != null) web.evaluateJavascript("window.bootNative(" + payload + ")", null);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't load the TV app list", Toast.LENGTH_LONG).show();
        }
    }

    private class Bridge {
        @JavascriptInterface public String vpnProviderInfo(){return protonShortcut.providerState();}
        @JavascriptInterface public void chooseVpnProvider(){runOnUiThread(()->protonShortcut.chooseProvider());}

        @JavascriptInterface public void chooseWallpaperSource(){runOnUiThread(()->wallpaperSources.choose());}
        @JavascriptInterface public void usePhotoWallpaper(){runOnUiThread(()->wallpaperSources.photos());}
        @JavascriptInterface public void nextVideoWallpaper(){runOnUiThread(()->wallpaperSources.next());}
        @JavascriptInterface public void wallpaperProviderSettings(){runOnUiThread(()->wallpaperSources.providerSettings());}

        @JavascriptInterface public void toggleProtonVpn(){
            runOnUiThread(()->protonShortcut.toggle());
        }

        @JavascriptInterface public void changeIconAppearance(String pkg) {
            if(pkg==null || !appEntries.containsKey(pkg))return;
            runOnUiThread(()->{
                ResolveInfo info=appEntries.get(pkg);
                if(info==null)return;
                iconPicker.show(info.loadLabel(getPackageManager()).toString(),value->{
                    getPreferences(MODE_PRIVATE).edit().putString("iconOverride:"+pkg,value).apply();
                    synchronized(iconCache){iconCache.remove(pkg);}
                    if(web!=null)web.evaluateJavascript("window.iconsChanged && window.iconsChanged()",null);
                    Toast.makeText(MainActivity.this,value.isEmpty()?"Automatic icon restored":"App icon updated",Toast.LENGTH_SHORT).show();
                });
            });
        }

        @JavascriptInterface public String availableIconPacks() {
            try { return new JSONObject().put("packs",iconPacks.discover()).put("selected",iconPackId).toString(); }
            catch(Exception ignored) { return "{}"; }
        }
        @JavascriptInterface public boolean selectIconPack(String pkg) {
            if (!iconPacks.select(pkg)) return false;
            iconPackId = pkg;
            synchronized(iconCache) { iconCache.evictAll(); }
            getPreferences(MODE_PRIVATE).edit().putString("iconPack",pkg).apply();
            return true;
        }

        @JavascriptInterface public void openApp(String pkg) {
            runOnUiThread(() -> {
                Intent launch = launchTargets.get(pkg);
                if (launch == null) {
                    Toast.makeText(MainActivity.this, "App not available", Toast.LENGTH_SHORT).show();
                    return;
                }
                try { startActivity(launch); }
                catch (Exception ignored) {
                    Toast.makeText(MainActivity.this, "Couldn't open this app", Toast.LENGTH_SHORT).show();
                }
            });
        }

        /** Returns the *installed* app's actual Android icon, never downloaded from the web. */
        @JavascriptInterface public String iconFor(String pkg) {
            if (pkg == null || !appEntries.containsKey(pkg)) return "";
            synchronized (iconCache) {
                String cached = iconCache.get(pkg);
                if (cached != null) return cached;
            }
            try {
                PackageManager pm = getPackageManager();
                ResolveInfo info = appEntries.get(pkg);
                JSONObject override=new JSONObject(getPreferences(MODE_PRIVATE).getString("iconOverride:"+pkg,"{}"));
                Drawable drawable=override.has("pack")?iconPacks.namedIcon(override.optString("pack"),override.optString("drawable")):null;
                if(drawable==null && !override.optBoolean("original"))drawable=iconPacks.icon(pkg, info == null ? "" : info.activityInfo.name);
                if (drawable == null && info != null) drawable = info.loadIcon(pm);
                if (drawable == null) drawable = pm.getApplicationIcon(pkg);
                if (drawable == null) return "";
                final int size = 96;
                Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, size, size);
                drawable.draw(canvas);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
                bitmap.recycle();
                String src = "data:image/png;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
                synchronized (iconCache) { iconCache.put(pkg, src); }
                return src;
            } catch (Exception | OutOfMemoryError ignored) {
                return ""; // UI will show a readable initial instead.
            }
        }

        @JavascriptInterface public void savePrefs(String raw) {
            if (raw != null && raw.length() < 25000) {
                getPreferences(MODE_PRIVATE).edit().putString("ui", raw).apply();
            }
        }

        /** Open Android's document picker; it grants access only to the chosen image. */
        @JavascriptInterface public void importWallpaper() {
            runOnUiThread(() -> {
                if (getImportedMetadata().length() >= MAX_IMPORTS) {
                    Toast.makeText(MainActivity.this, "Maximum of 20 imported wallpapers", Toast.LENGTH_LONG).show();
                    return;
                }
                Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                picker.setType("image/*");
                picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { startActivityForResult(picker, PICK_WALLPAPER); }
                catch (ActivityNotFoundException missing) {
                    // Some Android TV firmwares omit DocumentsUI; try another installed picker.
                    Intent alternate = new Intent(Intent.ACTION_GET_CONTENT);
                    alternate.addCategory(Intent.CATEGORY_OPENABLE);
                    alternate.setType("image/*");
                    alternate.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivityForResult(alternate, PICK_WALLPAPER); }
                    catch (ActivityNotFoundException notAvailable) {
                        Toast.makeText(MainActivity.this,
                                "No file picker found on this TV. Install a compatible TV file picker.",
                                Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface public String wallpaperImage(String id, boolean thumbnail) {
            if (!hasImportedId(id)) return "";
            File image = new File(wallpaperDirectory(), (thumbnail ? "thumb-" : "") + id + ".jpg");
            if (!image.isFile() || image.length() > 4000000) return "";
            try (InputStream in = new java.io.FileInputStream(image)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192]; int n;
                while ((n = in.read(chunk)) != -1) bytes.write(chunk, 0, n);
                return "data:image/jpeg;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
            } catch (IOException ignored) { return ""; }
        }

        @JavascriptInterface public boolean deleteWallpaper(String id) {
            synchronized (importsLock) {
                JSONArray imports = getImportedMetadata();
                JSONArray remaining = new JSONArray();
                boolean found = false;
                for (int i = 0; i < imports.length(); i++) {
                    JSONObject item = imports.optJSONObject(i);
                    if (item == null) continue;
                    if (id != null && id.equals(item.optString("id"))) found = true;
                    else remaining.put(item);
                }
                if (!found) return false;
                // Remove only files in our private wallpaper directory, never any source on USB.
                new File(wallpaperDirectory(), id + ".jpg").delete();
                new File(wallpaperDirectory(), "thumb-" + id + ".jpg").delete();
                getPreferences(MODE_PRIVATE).edit().putString("imports", remaining.toString()).apply();
                return true;
            }
        }

        /** A Home launcher must not close itself on Back, leaving an empty screen. */
        @JavascriptInterface public void close() {
            runOnUiThread(() -> {
                if (isSelectedHome()) {
                    Toast.makeText(MainActivity.this, "StillFrame is your Home screen", Toast.LENGTH_SHORT).show();
                } else finish();
            });
        }

        /** Open the TV's own Android settings, not StillFrame's display preferences. */
        @JavascriptInterface public void openAndroidSettings() {
            runOnUiThread(() -> {
                // Prefer the real Android TV Settings package on this Sharp model.
                // Never assume a private internal Activity class name.
                try {
                    Intent tvSettings = new Intent(Settings.ACTION_SETTINGS);
                    tvSettings.setPackage("com.android.tv.settings");
                    startActivity(tvSettings);
                    return;
                } catch (ActivityNotFoundException | SecurityException ignored) {
                    // Not all Android TV builds export the same settings entry point.
                }
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                    return;
                } catch (ActivityNotFoundException | SecurityException ignored) {
                }
                try {
                    Intent fallback = getPackageManager().getLeanbackLaunchIntentForPackage("com.android.tv.settings");
                    if (fallback == null) fallback = getPackageManager().getLaunchIntentForPackage("com.android.tv.settings");
                    if (fallback != null) {
                        startActivity(fallback);
                        return;
                    }
                } catch (Exception ignored) {
                }
                Toast.makeText(MainActivity.this, "Android TV Settings could not be opened", Toast.LENGTH_LONG).show();
            });
        }


    }


    /** True only when Android currently resolves HOME to StillFrame, or launched us as HOME. */
    private boolean isSelectedHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager roles = getSystemService(RoleManager.class);
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)
                        && roles.isRoleHeld(RoleManager.ROLE_HOME)) return true;
            }
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo chosen = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (chosen != null && chosen.activityInfo != null
                    && getPackageName().equals(chosen.activityInfo.packageName)) return true;
            return getIntent() != null && getIntent().hasCategory(Intent.CATEGORY_HOME);
        } catch (Exception ignored) { return false; }
    }

    private File wallpaperDirectory() {
        File dir = new File(getFilesDir(), "wallpapers");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private JSONArray getImportedMetadata() {
        try { return new JSONArray(getPreferences(MODE_PRIVATE).getString("imports", "[]")); }
        catch (JSONException ignored) { return new JSONArray(); }
    }

    private boolean hasImportedId(String id) {
        if (id == null || !id.matches("user-[0-9a-f\\-]{36}")) return false;
        synchronized (importsLock) {
            JSONArray list = getImportedMetadata();
            for (int i = 0; i < list.length(); i++)
                if (id.equals(list.optJSONObject(i) == null ? "" : list.optJSONObject(i).optString("id"))) return true;
        }
        return false;
    }

    private String safeImageName(Uri uri) {
        String name = "My wallpaper";
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn) && cursor.getLong(sizeColumn) > 25000000)
                    throw new IllegalArgumentException("Choose an image under 25 MB");
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameColumn >= 0) name = cursor.getString(nameColumn);
            }
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception ignored) {}
        if (name == null) name = "My wallpaper";
        name = name.replaceAll("(?i)\\.(jpg|jpeg|png|webp|heic)$", "").replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.length() > 45) name = name.substring(0, 45);
        return name.isEmpty() ? "My wallpaper" : name;
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if(wallpaperSources.result(request,result,data))return;
        if (request != PICK_WALLPAPER || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        new Thread(() -> {
            File full = null, thumb = null;
            try {
                ContentResolver resolver = getContentResolver();
                String mime = resolver.getType(uri);
                if (mime != null && !mime.startsWith("image/")) throw new IOException("Not an image");
                String name = safeImageName(uri);
                Bitmap decoded;
                if (Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.Source source = ImageDecoder.createSource(resolver, uri);
                    decoded = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                        int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                        if (w < 64 || h < 64 || w > 15000 || h > 15000)
                            throw new IllegalArgumentException("Unsupported image size");
                        float scale = Math.min(1f, Math.min(1920f / w, 1080f / h));
                        decoder.setTargetSize(Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)));
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    });
                } else {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    try (InputStream in = resolver.openInputStream(uri)) { BitmapFactory.decodeStream(in, null, opts); }
                    if (opts.outWidth < 64 || opts.outHeight < 64 || opts.outWidth > 15000 || opts.outHeight > 15000)
                        throw new IOException("Unsupported image size");
                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = 1;
                    while (opts.outWidth / opts.inSampleSize > 1920 || opts.outHeight / opts.inSampleSize > 1080) opts.inSampleSize *= 2;
                    try (InputStream in = resolver.openInputStream(uri)) { decoded = BitmapFactory.decodeStream(in, null, opts); }
                }
                if (decoded == null) throw new IOException("Could not decode image");
                String id = "user-" + UUID.randomUUID().toString();
                File dir = wallpaperDirectory();
                full = new File(dir, id + ".jpg");
                thumb = new File(dir, "thumb-" + id + ".jpg");
                try {
                    try (FileOutputStream output = new FileOutputStream(full)) {
                        if (!decoded.compress(Bitmap.CompressFormat.JPEG, 84, output)) throw new IOException("Could not save image");
                    }
                    float ratio = Math.min(320f / decoded.getWidth(), 180f / decoded.getHeight());
                    Bitmap small = Bitmap.createScaledBitmap(decoded,
                            Math.max(1, Math.round(decoded.getWidth() * ratio)),
                            Math.max(1, Math.round(decoded.getHeight() * ratio)), true);
                    try (FileOutputStream output = new FileOutputStream(thumb)) {
                        if (!small.compress(Bitmap.CompressFormat.JPEG, 76, output)) throw new IOException("Could not save preview");
                    }
                    if (small != decoded) small.recycle();
                } finally { decoded.recycle(); }
                JSONObject entry = new JSONObject(); entry.put("id", id); entry.put("name", name);
                synchronized (importsLock) {
                    JSONArray old = getImportedMetadata();
                    if (old.length() >= MAX_IMPORTS) throw new IOException("Wallpaper gallery is full (20 imports)");
                    old.put(entry);
                    getPreferences(MODE_PRIVATE).edit().putString("imports", old.toString()).apply();
                }
                runOnUiThread(() -> {
                    if (web != null) web.evaluateJavascript("window.onWallpaperImported(" + entry.toString() + ")", null);
                });
            } catch (Exception | OutOfMemoryError failure) {
                if (full != null) full.delete();
                if (thumb != null) thumb.delete();
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Import failed: " + (failure.getMessage() == null ? "try a smaller JPG" : failure.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        }, "StillFrameWallpaperImport").start();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        final int code = event.getKeyCode();
        final boolean select = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        // Delay normal OK until key-up so a long press cannot accidentally launch an app.
        if (select && web != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    okHeld = false;
                    cancelPendingHold();
                    holdHandler.postDelayed(holdAction, 650L);
                }
                if (event.isLongPress() && !okHeld) {
                    cancelPendingHold();
                    okHeld = true;
                    sendRemoteKey("hold");
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                cancelPendingHold();
                if (!okHeld && !event.isCanceled()) sendRemoteKey("select");
                okHeld = false;
                return true;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && web != null) {
            String action;
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT: action = "left"; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: action = "right"; break;
                case KeyEvent.KEYCODE_DPAD_UP: action = "up"; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: action = "down"; break;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MENU: action = "pin"; break;
                default: action = null;
            }
            if (action != null) {
                if (event.getRepeatCount() == 0) sendRemoteKey(action);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onPause() {
        if(protonShortcut!=null)protonShortcut.pause();
        if(wallpaperSources!=null)wallpaperSources.pause();
        cancelPendingHold();
        networkHandler.removeCallbacks(networkTick);
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (web != null) web.evaluateJavascript("window.tvRemote('back')", null);
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if(protonShortcut!=null)protonShortcut.destroy();
        if(wallpaperSources!=null)wallpaperSources.destroy();
        if(iconPicker!=null)iconPicker.dismiss();
        networkHandler.removeCallbacks(networkTick);
        cancelPendingHold();
        if (web != null) {
            web.removeJavascriptInterface("StillFrameAndroid");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
