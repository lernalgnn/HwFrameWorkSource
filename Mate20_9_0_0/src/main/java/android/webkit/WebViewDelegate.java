package android.webkit;

import android.annotation.SystemApi;
import android.app.ActivityThread;
import android.app.Application;
import android.app.ResourcesManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.Trace;
import android.util.SparseArray;
import android.view.DisplayListCanvas;
import android.view.View;
import android.view.ViewRootImpl;
import com.android.internal.util.ArrayUtils;

@SystemApi
public final class WebViewDelegate {

    public interface OnTraceEnabledChangeListener {
        void onTraceEnabledChange(boolean z);
    }

    WebViewDelegate() {
    }

    public void setOnTraceEnabledChangeListener(final OnTraceEnabledChangeListener listener) {
        SystemProperties.addChangeCallback(new Runnable() {
            public void run() {
                listener.onTraceEnabledChange(WebViewDelegate.this.isTraceTagEnabled());
            }
        });
    }

    public boolean isTraceTagEnabled() {
        return Trace.isTagEnabled(16);
    }

    public boolean canInvokeDrawGlFunctor(View containerView) {
        return true;
    }

    public void invokeDrawGlFunctor(View containerView, long nativeDrawGLFunctor, boolean waitForCompletion) {
        ViewRootImpl.invokeFunctor(nativeDrawGLFunctor, waitForCompletion);
    }

    public void callDrawGlFunction(Canvas canvas, long nativeDrawGLFunctor) {
        if (canvas instanceof DisplayListCanvas) {
            ((DisplayListCanvas) canvas).drawGLFunctor2(nativeDrawGLFunctor, null);
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(canvas.getClass().getName());
        stringBuilder.append(" is not a DisplayList canvas");
        throw new IllegalArgumentException(stringBuilder.toString());
    }

    public void callDrawGlFunction(Canvas canvas, long nativeDrawGLFunctor, Runnable releasedRunnable) {
        if (canvas instanceof DisplayListCanvas) {
            ((DisplayListCanvas) canvas).drawGLFunctor2(nativeDrawGLFunctor, releasedRunnable);
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(canvas.getClass().getName());
        stringBuilder.append(" is not a DisplayList canvas");
        throw new IllegalArgumentException(stringBuilder.toString());
    }

    public void detachDrawGlFunctor(View containerView, long nativeDrawGLFunctor) {
        ViewRootImpl viewRootImpl = containerView.getViewRootImpl();
        if (nativeDrawGLFunctor != 0 && viewRootImpl != null) {
            viewRootImpl.detachFunctor(nativeDrawGLFunctor);
        }
    }

    public int getPackageId(Resources resources, String packageName) {
        SparseArray<String> packageIdentifiers = resources.getAssets().getAssignedPackageIdentifiers();
        for (int i = 0; i < packageIdentifiers.size(); i++) {
            if (packageName.equals((String) packageIdentifiers.valueAt(i))) {
                return packageIdentifiers.keyAt(i);
            }
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Package not found: ");
        stringBuilder.append(packageName);
        throw new RuntimeException(stringBuilder.toString());
    }

    public Application getApplication() {
        return ActivityThread.currentApplication();
    }

    public String getErrorString(Context context, int errorCode) {
        return LegacyErrorStrings.getString(errorCode, context);
    }

    public void addWebViewAssetPath(Context context) {
        String newAssetPath = WebViewFactory.getLoadedPackageInfo().applicationInfo.sourceDir;
        ApplicationInfo appInfo = context.getApplicationInfo();
        String[] libs = appInfo.sharedLibraryFiles;
        if (!ArrayUtils.contains(libs, newAssetPath)) {
            int newLibAssetsCount = 1 + (libs != null ? libs.length : 0);
            String[] newLibAssets = new String[newLibAssetsCount];
            if (libs != null) {
                System.arraycopy(libs, 0, newLibAssets, 0, libs.length);
            }
            newLibAssets[newLibAssetsCount - 1] = newAssetPath;
            appInfo.sharedLibraryFiles = newLibAssets;
            ResourcesManager.getInstance().appendLibAssetForMainAssetPath(appInfo.getBaseResourcePath(), newAssetPath);
        }
    }

    public boolean isMultiProcessEnabled() {
        try {
            return WebViewFactory.getUpdateService().isMultiProcessEnabled();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public String getDataDirectorySuffix() {
        return WebViewFactory.getDataDirectorySuffix();
    }
}
