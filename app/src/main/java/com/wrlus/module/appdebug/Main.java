package com.wrlus.module.appdebug;

import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Debug;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Field;

public class Main implements IXposedHookLoadPackage {
    public static final String TAG = "AppDebuggable";
    private static final String serverPackageImpl = "com.android.server.pm.parsing.pkg.PackageImpl";
    private static final String internalPackageImpl = "com.android.internal.pm.parsing.pkg.PackageImpl";
    private static final String parsingPackageImpl = "android.content.pm.parsing.ParsingPackageImpl";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if ("android".equals(loadPackageParam.packageName) || "system".equals(loadPackageParam.packageName)) {
            hookSysServer(loadPackageParam.classLoader);
        } else {
            // hookAppAntiDebug(loadPackageParam.packageName);
        }
    }
    private void hookSysServer(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("android.content.pm.PackageParser", classLoader,
                "parseBaseApplication",
                "android.content.pm.PackageParser.Package",
                Resources.class, XmlResourceParser.class, int.class, String[].class,
                new XC_MethodHook() {

                    @SuppressLint({"DiscouragedPrivateApi", "PrivateApi"})
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Field aiField = XposedHelpers.findClass("android.content.pm.PackageParser.Package", classLoader)
                                    .getDeclaredField("applicationInfo");
                            aiField.setAccessible(true);
                            final ApplicationInfo ai = (ApplicationInfo) aiField.get(param.args[0]);
                            if (ai != null) {
                                ai.flags |= ApplicationInfo.FLAG_DEBUGGABLE;
                                Log.i(TAG, "Set debuggable flag for package: " + ai.packageName + " via PackageParser");
                            } else {
                                Log.e(TAG, "Error when hooking parseBaseApplication:" +
                                        " applicationInfo is null");
                            }
                        } catch (ReflectiveOperationException e) {
                            Log.e(TAG, "ReflectiveOperationException", e);
                        }
                    }
                });
        if (XposedHelpers.findClassIfExists(serverPackageImpl, classLoader) != null) {
            hookSetDebuggable(serverPackageImpl, classLoader);
        }
        if (XposedHelpers.findClassIfExists(internalPackageImpl, classLoader) != null) {
            hookSetDebuggable(internalPackageImpl, classLoader);
        }
        // android.* package is in system default class loader.
        if (XposedHelpers.findClassIfExists(parsingPackageImpl, ClassLoader.getSystemClassLoader()) != null) {
            hookSetDebuggable(parsingPackageImpl, classLoader);
        }
    }

    private void hookSetDebuggable(String className, ClassLoader classLoader) {
        if (XposedHelpers.findMethodExactIfExists(className, classLoader,
                "setDebuggable", boolean.class) == null) {
            Log.d(TAG, "No setDebuggable method in class: " + className);
            return;
        }
        Log.d(TAG, "Matched PackageParser2 class name: " + className);
        XposedHelpers.findAndHookMethod(className, classLoader,
                "setDebuggable", boolean.class,
                new XC_MethodHook() {

                    @SuppressLint({"DiscouragedPrivateApi", "PrivateApi"})
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Field pkgNameField = XposedHelpers.findClass(className, classLoader)
                                    .getDeclaredField("packageName");
                            pkgNameField.setAccessible(true);
                            final String packageName = (String) pkgNameField.get(param.thisObject);
                            boolean isDebuggable = (boolean) param.args[0];
                            if (!isDebuggable) {
                                Log.i(TAG, "Set debuggable flag for package: " + packageName + " via PackageParser2");
                                param.args[0] = true;
                            }
                        } catch (ReflectiveOperationException e) {
                            Log.e(TAG, "ReflectiveOperationException", e);
                        }
                    }
                });
    }

    private void hookAppAntiDebug(String packageName) {
        XposedHelpers.findAndHookMethod(Debug.class, "isDebuggerConnected",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Log.i(TAG, "Block isDebuggerConnected check for package: " + packageName);
                        param.setResult(false);
                    }
                });
    }
}
