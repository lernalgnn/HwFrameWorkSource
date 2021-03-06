package com.android.server.rms.iaware;

import android.content.Context;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.HandlerThread;
import android.os.SELinux;
import android.rms.iaware.AwareConstant;
import android.rms.iaware.AwareConstant.FeatureType;
import android.rms.iaware.AwareConstant.ResourceType;
import android.rms.iaware.AwareLog;
import android.rms.iaware.CollectData;
import android.rms.iaware.DumpData;
import android.rms.iaware.IReportDataCallback;
import android.rms.iaware.StatisticsData;
import android.system.ErrnoException;
import android.system.Os;
import com.android.server.rms.iaware.feature.DevSchedFeatureRT;
import com.android.server.rms.iaware.memory.policy.DMEServer;
import com.android.server.rms.iaware.memory.utils.MemoryDumpsys;
import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

public class RDAService {
    private static final int DEFAULT_MAX_TIME = 60;
    private static final String TAG = "RDAService";
    private static String TARGET_APP_FILE_PATH = "/data/app_acc";
    private ArrayList<String> mFakeForegroundList = new ArrayList();
    private final ReentrantReadWriteLock mPreRecogLock = new ReentrantReadWriteLock();
    private RFeatureManager mRFeatureManager = null;
    private boolean rdaInitStatus = false;

    private boolean getRDAInitStatus() {
        return this.rdaInitStatus;
    }

    private void setRDAInitStatus(boolean initStatus) {
        this.rdaInitStatus = initStatus;
    }

    public RDAService(Context context, HandlerThread handlerThread) {
        AwareLog.d(TAG, "RDAService construct!");
        this.mRFeatureManager = new RFeatureManager(context, handlerThread);
        DMEServer.getInstance().setHandler(handlerThread);
        ensureAppAccDirectoryCreated();
        prepareCloudDownloading();
    }

    public void reportData(CollectData data) {
        if (data == null || ResourceType.getResourceType(data.getResId()) == ResourceType.RESOURCE_INVALIDE_TYPE) {
            AwareLog.e(TAG, "Reporting error data!");
        } else {
            this.mRFeatureManager.reportData(data);
        }
    }

    public void reportDataWithCallback(CollectData data, IReportDataCallback callback) {
        if (data == null || callback == null) {
            AwareLog.e(TAG, "reportDataWithCallback parameters error!");
        } else {
            this.mRFeatureManager.reportDataWithCallback(data, callback);
        }
    }

    public void enableFeature(int type) {
        if (FeatureType.getFeatureType(type) == FeatureType.FEATURE_INVALIDE_TYPE) {
            AwareLog.e(TAG, "Enabling error feature type!");
        } else {
            this.mRFeatureManager.enableFeature(type);
        }
    }

    public void disableFeature(int type) {
        if (FeatureType.getFeatureType(type) == FeatureType.FEATURE_INVALIDE_TYPE) {
            AwareLog.e(TAG, "Disabling error feature type!");
        } else {
            this.mRFeatureManager.disableFeature(type);
        }
    }

    public void init(Bundle bundle) {
        if (bundle == null) {
            AwareLog.e(TAG, "init error bundle data!");
            return;
        }
        if (getRDAInitStatus()) {
            AwareLog.d(TAG, "RDA update iaware feature switch status from config file");
            syncFeatureStatus(bundle);
        } else {
            this.mRFeatureManager.init(bundle);
            setRDAInitStatus(true);
        }
    }

    public boolean isResourceNeeded(int resourceid) {
        ResourceType resourceType = ResourceType.getResourceType(resourceid);
        if (resourceType != ResourceType.RESOURCE_INVALIDE_TYPE) {
            return this.mRFeatureManager.isResourceNeeded(resourceType);
        }
        AwareLog.e(TAG, "isResourceNeeded error resource type!");
        return false;
    }

    public int isFeatureEnabled(int featureId) {
        return this.mRFeatureManager.isFeatureEnabled(featureId);
    }

    public boolean configUpdate() {
        return this.mRFeatureManager.configUpdate();
    }

    public boolean custConfigUpdate() {
        return this.mRFeatureManager.custConfigUpdate();
    }

    public int getDumpData(int time, List<DumpData> dumpData) {
        int dumpDataTime = time;
        if (dumpDataTime <= 0 || dumpDataTime > 60) {
            AwareLog.e(TAG, "getDumpData parameter error: use default time!");
            dumpDataTime = 60;
        }
        if (dumpData != null) {
            return this.mRFeatureManager.getDumpData(dumpDataTime, dumpData);
        }
        AwareLog.e(TAG, "getDumpData parameter error: null list!");
        return 0;
    }

    public int getStatisticsData(List<StatisticsData> statisticsData) {
        if (statisticsData != null) {
            return this.mRFeatureManager.getStatisticsData(statisticsData);
        }
        AwareLog.e(TAG, "statisticsData parameter error: null list!");
        return 0;
    }

    public String saveBigData(int featureId, boolean clear) {
        return this.mRFeatureManager.saveBigData(featureId, clear);
    }

    public String fetchBigDataByVersion(int iVer, int fId, boolean beta, boolean clear) {
        return this.mRFeatureManager.fetchBigDataByVersion(iVer, fId, beta, clear);
    }

    public String fetchDFTDataByVersion(int iVer, int fId, boolean beta, boolean clear, boolean betaEncode) {
        return this.mRFeatureManager.fetchDFTDataByVersion(iVer, fId, beta, clear, betaEncode);
    }

    public void updateFakeForegroundList(List<String> processList) {
        WriteLock writeLock = this.mPreRecogLock.writeLock();
        writeLock.lock();
        try {
            if (!this.mFakeForegroundList.isEmpty()) {
                this.mFakeForegroundList.clear();
            }
            if (processList != null) {
                if (!processList.isEmpty()) {
                    this.mFakeForegroundList.addAll(processList);
                    writeLock.unlock();
                    return;
                }
            }
            writeLock.unlock();
        } catch (Throwable th) {
            writeLock.unlock();
        }
    }

    public boolean isFakeForegroundProcess(String process) {
        if (process == null || process.length() == 0) {
            AwareLog.e(TAG, "isFakeForegroundProcess parameter is null!");
            return false;
        } else if (this.mPreRecogLock.isWriteLocked()) {
            AwareLog.w(TAG, "The fake list is updating!");
            return false;
        } else {
            ReadLock readLock = this.mPreRecogLock.readLock();
            readLock.lock();
            try {
                if (this.mFakeForegroundList.isEmpty()) {
                    return false;
                }
                Iterator it = this.mFakeForegroundList.iterator();
                while (it.hasNext()) {
                    if (process.equals((String) it.next())) {
                        readLock.unlock();
                        return true;
                    }
                }
                readLock.unlock();
                return false;
            } finally {
                readLock.unlock();
            }
        }
    }

    public boolean isEnableFakeForegroundControl() {
        if (this.mPreRecogLock.isWriteLocked()) {
            AwareLog.w(TAG, "The fake list is updating!");
            return false;
        }
        ReadLock readLock = this.mPreRecogLock.readLock();
        readLock.lock();
        try {
            if (this.mFakeForegroundList.isEmpty()) {
                return false;
            }
            readLock.unlock();
            return true;
        } finally {
            readLock.unlock();
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        MemoryDumpsys.doDumpsys(this.mRFeatureManager.getRegisteredFeature(FeatureType.FEATURE_MEMORY), fd, pw, args);
        DevSchedFeatureRT.doDumpsys(this.mRFeatureManager.getRegisteredFeature(FeatureType.FEATURE_DEVSCHED), fd, pw, args);
    }

    private void ensureAppAccDirectoryCreated() {
        File appDir = new File(TARGET_APP_FILE_PATH);
        if (!appDir.exists()) {
            if (appDir.mkdirs()) {
                try {
                    Os.chmod(TARGET_APP_FILE_PATH, 457);
                } catch (ErrnoException e) {
                    AwareLog.e(TAG, "ensureAppAccDirectoryCreated chmod error!");
                }
                return;
            }
            AwareLog.e(TAG, "ensureAppAccDirectoryCreated mkdir error");
        }
    }

    private void syncFeatureStatus(Bundle bundle) {
        FeatureType[] featureTypes = this.mRFeatureManager.getFeatureTypes();
        int featureNum = featureTypes.length;
        boolean featureEnabled = false;
        for (int index = 0; index < featureNum; index++) {
            int[] featureInfo = this.mRFeatureManager.getFeatureInfoFromBundle(bundle, featureTypes[index].name());
            int i = featureInfo[0];
            RFeatureManager rFeatureManager = this.mRFeatureManager;
            boolean z = true;
            if (i != 1) {
                z = false;
            }
            featureEnabled = z;
            if (this.mRFeatureManager.getFeatureStatus(featureTypes[index].ordinal()) != featureInfo[0]) {
                AwareLog.d(TAG, "xml != real status");
                if (featureEnabled) {
                    this.mRFeatureManager.enableFeature(featureTypes[index].ordinal());
                } else {
                    this.mRFeatureManager.disableFeature(featureTypes[index].ordinal());
                }
            } else {
                AwareLog.d(TAG, "xml == real status");
            }
        }
    }

    private void prepareCloudDownloading() {
        String str;
        StringBuilder stringBuilder;
        String filePath = AwareConstant.getConfigFilesPath();
        File fileDir = new File(filePath);
        if (!fileDir.exists()) {
            str = TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("Create ");
            stringBuilder.append(filePath);
            AwareLog.i(str, stringBuilder.toString());
            if (fileDir.mkdir()) {
                str = TAG;
                stringBuilder = new StringBuilder();
                stringBuilder.append("setPermissions ");
                stringBuilder.append(filePath);
                AwareLog.i(str, stringBuilder.toString());
                if (FileUtils.setPermissions(filePath, 504, -1, -1) != 0) {
                    str = TAG;
                    stringBuilder = new StringBuilder();
                    stringBuilder.append("Failed to setPermissions ");
                    stringBuilder.append(filePath);
                    AwareLog.e(str, stringBuilder.toString());
                }
            } else {
                str = TAG;
                stringBuilder = new StringBuilder();
                stringBuilder.append("Failed to create ");
                stringBuilder.append(filePath);
                AwareLog.e(str, stringBuilder.toString());
                return;
            }
        }
        str = TAG;
        stringBuilder = new StringBuilder();
        stringBuilder.append("Reflash SELinux lable for ");
        stringBuilder.append(filePath);
        AwareLog.i(str, stringBuilder.toString());
        if (!SELinux.restoreconRecursive(fileDir)) {
            AwareLog.e(TAG, "Failed to reflash SELinux lable");
        }
    }
}
