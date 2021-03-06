package com.android.server.am;

import android.app.INotificationManager;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ComponentName.WithComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.Intent.FilterComparison;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Flog;
import android.util.Slog;
import android.util.TimeUtils;
import android.util.proto.ProtoOutputStream;
import android.util.proto.ProtoUtils;
import com.android.internal.app.procstats.ServiceState;
import com.android.internal.os.BatteryStatsImpl.Uid.Pkg.Serv;
import com.android.server.LocalServices;
import com.android.server.notification.NotificationManagerInternal;
import com.android.server.pm.DumpState;
import com.android.server.pm.PackageManagerService;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ServiceRecord extends Binder implements WithComponentName {
    static final int MAX_DELIVERY_COUNT = 3;
    static final int MAX_DONE_EXECUTING_COUNT = 6;
    private static final String TAG = "ActivityManager";
    final ActivityManagerService ams;
    public ProcessRecord app;
    ApplicationInfo appInfo;
    final ArrayMap<FilterComparison, IntentBindRecord> bindings = new ArrayMap();
    boolean callStart;
    final ArrayMap<IBinder, ArrayList<ConnectionRecord>> connections = new ArrayMap();
    int crashCount;
    final long createRealTime;
    boolean createdFromFg;
    boolean delayed;
    boolean delayedStop;
    final ArrayList<StartItem> deliveredStarts = new ArrayList();
    long destroyTime;
    boolean destroying;
    boolean executeFg;
    int executeNesting;
    long executingStart;
    final boolean exported;
    boolean fgRequired;
    boolean fgWaiting;
    int foregroundId;
    Notification foregroundNoti;
    final FilterComparison intent;
    boolean isForeground;
    ProcessRecord isolatedProc;
    long lastActivity;
    private int lastStartId;
    final ComponentName name;
    long nextRestartTime;
    final String packageName;
    final ArrayList<StartItem> pendingStarts = new ArrayList();
    final String permission;
    final String processName;
    int restartCount;
    long restartDelay;
    long restartTime;
    ServiceState restartTracker;
    final Runnable restarter;
    final ServiceInfo serviceInfo;
    final String shortName;
    boolean startRequested;
    long startingBgTimeout;
    final Serv stats;
    boolean stopIfKilled;
    String stringName;
    int totalRestartCount;
    ServiceState tracker;
    final int userId;
    boolean whitelistManager;

    static class StartItem {
        final int callingId;
        long deliveredTime;
        int deliveryCount;
        int doneExecutingCount;
        final int id;
        final Intent intent;
        final NeededUriGrants neededGrants;
        final ServiceRecord sr;
        String stringName;
        final boolean taskRemoved;
        UriPermissionOwner uriPermissions;

        StartItem(ServiceRecord _sr, boolean _taskRemoved, int _id, Intent _intent, NeededUriGrants _neededGrants, int _callingId) {
            this.sr = _sr;
            this.taskRemoved = _taskRemoved;
            this.id = _id;
            this.intent = _intent;
            this.neededGrants = _neededGrants;
            this.callingId = _callingId;
        }

        UriPermissionOwner getUriPermissionsLocked() {
            if (this.uriPermissions == null) {
                this.uriPermissions = new UriPermissionOwner(this.sr.ams, this);
            }
            return this.uriPermissions;
        }

        void removeUriPermissionsLocked() {
            if (this.uriPermissions != null) {
                this.uriPermissions.removeUriPermissionsLocked();
                this.uriPermissions = null;
            }
        }

        public void writeToProto(ProtoOutputStream proto, long fieldId, long now) {
            ProtoOutputStream protoOutputStream = proto;
            long token = protoOutputStream.start(fieldId);
            protoOutputStream.write(1120986464257L, this.id);
            ProtoUtils.toDuration(protoOutputStream, 1146756268034L, this.deliveredTime, now);
            protoOutputStream.write(1120986464259L, this.deliveryCount);
            protoOutputStream.write(1120986464260L, this.doneExecutingCount);
            if (this.intent != null) {
                this.intent.writeToProto(protoOutputStream, 1146756268037L, true, true, true, false);
            }
            if (this.neededGrants != null) {
                this.neededGrants.writeToProto(protoOutputStream, 1146756268038L);
            }
            if (this.uriPermissions != null) {
                this.uriPermissions.writeToProto(protoOutputStream, 1146756268039L);
            }
            protoOutputStream.end(token);
        }

        public String toString() {
            if (this.stringName != null) {
                return this.stringName;
            }
            StringBuilder sb = new StringBuilder(128);
            sb.append("ServiceRecord{");
            sb.append(Integer.toHexString(System.identityHashCode(this.sr)));
            sb.append(' ');
            sb.append(this.sr.shortName);
            sb.append(" StartItem ");
            sb.append(Integer.toHexString(System.identityHashCode(this)));
            sb.append(" id=");
            sb.append(this.id);
            sb.append('}');
            String stringBuilder = sb.toString();
            this.stringName = stringBuilder;
            return stringBuilder;
        }
    }

    void dumpStartList(PrintWriter pw, String prefix, List<StartItem> list, long now) {
        int N = list.size();
        for (int i = 0; i < N; i++) {
            StartItem si = (StartItem) list.get(i);
            pw.print(prefix);
            pw.print("#");
            pw.print(i);
            pw.print(" id=");
            pw.print(si.id);
            if (now != 0) {
                pw.print(" dur=");
                TimeUtils.formatDuration(si.deliveredTime, now, pw);
            }
            if (si.deliveryCount != 0) {
                pw.print(" dc=");
                pw.print(si.deliveryCount);
            }
            if (si.doneExecutingCount != 0) {
                pw.print(" dxc=");
                pw.print(si.doneExecutingCount);
            }
            pw.println(BackupManagerConstants.DEFAULT_BACKUP_FINISHED_NOTIFICATION_RECEIVERS);
            pw.print(prefix);
            pw.print("  intent=");
            if (si.intent != null) {
                pw.println(si.intent.toString());
            } else {
                pw.println("null");
            }
            if (si.neededGrants != null) {
                pw.print(prefix);
                pw.print("  neededGrants=");
                pw.println(si.neededGrants);
            }
            if (si.uriPermissions != null) {
                si.uriPermissions.dump(pw, prefix);
            }
        }
    }

    void writeToProto(ProtoOutputStream proto, long fieldId) {
        long appInfoToken;
        long j;
        long executNestingToken;
        int N;
        int i;
        int i2;
        int i3;
        ProtoOutputStream protoOutputStream = proto;
        long token = proto.start(fieldId);
        protoOutputStream.write(1138166333441L, this.shortName);
        protoOutputStream.write(1133871366146L, this.app != null);
        if (this.app != null) {
            protoOutputStream.write(1120986464259L, this.app.pid);
        }
        if (this.intent != null) {
            this.intent.getIntent().writeToProto(protoOutputStream, 1146756268036L, false, true, false, true);
        }
        protoOutputStream.write(1138166333445L, this.packageName);
        protoOutputStream.write(1138166333446L, this.processName);
        protoOutputStream.write(1138166333447L, this.permission);
        long now = SystemClock.uptimeMillis();
        long nowReal = SystemClock.elapsedRealtime();
        if (this.appInfo != null) {
            appInfoToken = protoOutputStream.start(1146756268040L);
            protoOutputStream.write(1138166333441L, this.appInfo.sourceDir);
            if (!Objects.equals(this.appInfo.sourceDir, this.appInfo.publicSourceDir)) {
                protoOutputStream.write(1138166333442L, this.appInfo.publicSourceDir);
            }
            protoOutputStream.write(1138166333443L, this.appInfo.dataDir);
            protoOutputStream.end(appInfoToken);
        }
        if (this.app != null) {
            this.app.writeToProto(protoOutputStream, 1146756268041L);
        }
        if (this.isolatedProc != null) {
            this.isolatedProc.writeToProto(protoOutputStream, 1146756268042L);
        }
        protoOutputStream.write(1133871366155L, this.whitelistManager);
        protoOutputStream.write(1133871366156L, this.delayed);
        if (this.isForeground || this.foregroundId != 0) {
            appInfoToken = protoOutputStream.start(1146756268045L);
            protoOutputStream.write(1120986464257L, this.foregroundId);
            this.foregroundNoti.writeToProto(protoOutputStream, 1146756268034L);
            protoOutputStream.end(appInfoToken);
        }
        ProtoOutputStream protoOutputStream2 = protoOutputStream;
        ProtoUtils.toDuration(protoOutputStream2, 1146756268046L, this.createRealTime, nowReal);
        long j2 = now;
        ProtoUtils.toDuration(protoOutputStream2, 1146756268047L, this.startingBgTimeout, j2);
        ProtoUtils.toDuration(protoOutputStream2, 1146756268048L, this.lastActivity, j2);
        ProtoUtils.toDuration(protoOutputStream2, 1146756268049L, this.restartTime, j2);
        protoOutputStream.write(1133871366162L, this.createdFromFg);
        if (this.startRequested || this.delayedStop || this.lastStartId != 0) {
            appInfoToken = protoOutputStream.start(1146756268051L);
            protoOutputStream.write(1133871366145L, this.startRequested);
            j = 1133871366146L;
            protoOutputStream.write(1133871366146L, this.delayedStop);
            protoOutputStream.write(1133871366147L, this.stopIfKilled);
            protoOutputStream.write(1120986464261L, this.lastStartId);
            protoOutputStream.end(appInfoToken);
        } else {
            j = 1133871366146L;
        }
        if (this.executeNesting != 0) {
            j2 = protoOutputStream.start(1146756268052L);
            protoOutputStream.write(1120986464257L, this.executeNesting);
            protoOutputStream.write(j, this.executeFg);
            executNestingToken = j2;
            ProtoUtils.toDuration(protoOutputStream, 1146756268035L, this.executingStart, now);
            protoOutputStream.end(executNestingToken);
        }
        if (this.destroying || this.destroyTime != 0) {
            ProtoUtils.toDuration(protoOutputStream, 1146756268053L, this.destroyTime, now);
        }
        if (!(this.crashCount == 0 && this.restartCount == 0 && this.restartDelay == 0 && this.nextRestartTime == 0)) {
            executNestingToken = protoOutputStream.start(1146756268054L);
            protoOutputStream.write(1120986464257L, this.restartCount);
            protoOutputStream2 = protoOutputStream;
            j2 = now;
            ProtoUtils.toDuration(protoOutputStream2, 1146756268034L, this.restartDelay, j2);
            ProtoUtils.toDuration(protoOutputStream2, 1146756268035L, this.nextRestartTime, j2);
            protoOutputStream.write(1120986464260L, this.crashCount);
            protoOutputStream.end(executNestingToken);
        }
        if (this.deliveredStarts.size() > 0) {
            N = this.deliveredStarts.size();
            i = 0;
            while (true) {
                i2 = i;
                if (i2 >= N) {
                    break;
                }
                ((StartItem) this.deliveredStarts.get(i2)).writeToProto(protoOutputStream, 2246267895831L, now);
                i = i2 + 1;
            }
        }
        if (this.pendingStarts.size() > 0) {
            N = this.pendingStarts.size();
            i = 0;
            while (true) {
                i2 = i;
                if (i2 >= N) {
                    break;
                }
                ((StartItem) this.pendingStarts.get(i2)).writeToProto(protoOutputStream, 2246267895832L, now);
                i = i2 + 1;
            }
        }
        if (this.bindings.size() > 0) {
            i = this.bindings.size();
            for (i3 = 0; i3 < i; i3++) {
                ((IntentBindRecord) this.bindings.valueAt(i3)).writeToProto(protoOutputStream, 2246267895833L);
            }
        }
        if (this.connections.size() > 0) {
            i = this.connections.size();
            for (i3 = 0; i3 < i; i3++) {
                ArrayList<ConnectionRecord> c = (ArrayList) this.connections.valueAt(i3);
                for (int i4 = 0; i4 < c.size(); i4++) {
                    ((ConnectionRecord) c.get(i4)).writeToProto(protoOutputStream, 2246267895834L);
                }
            }
        }
        protoOutputStream.end(token);
    }

    void dump(PrintWriter pw, String prefix) {
        int i;
        pw.print(prefix);
        pw.print("intent={");
        pw.print(this.intent.getIntent().toShortString(true, true, false, true));
        pw.println('}');
        pw.print(prefix);
        pw.print("packageName=");
        pw.println(this.packageName);
        pw.print(prefix);
        pw.print("processName=");
        pw.println(this.processName);
        if (this.permission != null) {
            pw.print(prefix);
            pw.print("permission=");
            pw.println(this.permission);
        }
        long now = SystemClock.uptimeMillis();
        long nowReal = SystemClock.elapsedRealtime();
        if (this.appInfo != null) {
            pw.print(prefix);
            pw.print("baseDir=");
            pw.println(this.appInfo.sourceDir);
            if (!Objects.equals(this.appInfo.sourceDir, this.appInfo.publicSourceDir)) {
                pw.print(prefix);
                pw.print("resDir=");
                pw.println(this.appInfo.publicSourceDir);
            }
            pw.print(prefix);
            pw.print("dataDir=");
            pw.println(this.appInfo.dataDir);
        }
        pw.print(prefix);
        pw.print("app=");
        pw.println(this.app);
        if (this.isolatedProc != null) {
            pw.print(prefix);
            pw.print("isolatedProc=");
            pw.println(this.isolatedProc);
        }
        if (this.whitelistManager) {
            pw.print(prefix);
            pw.print("whitelistManager=");
            pw.println(this.whitelistManager);
        }
        if (this.delayed) {
            pw.print(prefix);
            pw.print("delayed=");
            pw.println(this.delayed);
        }
        if (this.isForeground || this.foregroundId != 0) {
            pw.print(prefix);
            pw.print("isForeground=");
            pw.print(this.isForeground);
            pw.print(" foregroundId=");
            pw.print(this.foregroundId);
            pw.print(" foregroundNoti=");
            pw.println(this.foregroundNoti);
        }
        pw.print(prefix);
        pw.print("createTime=");
        TimeUtils.formatDuration(this.createRealTime, nowReal, pw);
        pw.print(" startingBgTimeout=");
        TimeUtils.formatDuration(this.startingBgTimeout, now, pw);
        pw.println();
        pw.print(prefix);
        pw.print("lastActivity=");
        TimeUtils.formatDuration(this.lastActivity, now, pw);
        pw.print(" restartTime=");
        TimeUtils.formatDuration(this.restartTime, now, pw);
        pw.print(" createdFromFg=");
        pw.println(this.createdFromFg);
        if (this.startRequested || this.delayedStop || this.lastStartId != 0) {
            pw.print(prefix);
            pw.print("startRequested=");
            pw.print(this.startRequested);
            pw.print(" delayedStop=");
            pw.print(this.delayedStop);
            pw.print(" stopIfKilled=");
            pw.print(this.stopIfKilled);
            pw.print(" callStart=");
            pw.print(this.callStart);
            pw.print(" lastStartId=");
            pw.println(this.lastStartId);
        }
        if (this.executeNesting != 0) {
            pw.print(prefix);
            pw.print("executeNesting=");
            pw.print(this.executeNesting);
            pw.print(" executeFg=");
            pw.print(this.executeFg);
            pw.print(" executingStart=");
            TimeUtils.formatDuration(this.executingStart, now, pw);
            pw.println();
        }
        if (this.destroying || this.destroyTime != 0) {
            pw.print(prefix);
            pw.print("destroying=");
            pw.print(this.destroying);
            pw.print(" destroyTime=");
            TimeUtils.formatDuration(this.destroyTime, now, pw);
            pw.println();
        }
        if (!(this.crashCount == 0 && this.restartCount == 0 && this.restartDelay == 0 && this.nextRestartTime == 0)) {
            pw.print(prefix);
            pw.print("restartCount=");
            pw.print(this.restartCount);
            pw.print(" restartDelay=");
            TimeUtils.formatDuration(this.restartDelay, now, pw);
            pw.print(" nextRestartTime=");
            TimeUtils.formatDuration(this.nextRestartTime, now, pw);
            pw.print(" crashCount=");
            pw.println(this.crashCount);
        }
        if (this.deliveredStarts.size() > 0) {
            pw.print(prefix);
            pw.println("Delivered Starts:");
            dumpStartList(pw, prefix, this.deliveredStarts, now);
        }
        if (this.pendingStarts.size() > 0) {
            pw.print(prefix);
            pw.println("Pending Starts:");
            dumpStartList(pw, prefix, this.pendingStarts, 0);
        }
        if (this.bindings.size() > 0) {
            pw.print(prefix);
            pw.println("Bindings:");
            for (i = 0; i < this.bindings.size(); i++) {
                IntentBindRecord b = (IntentBindRecord) this.bindings.valueAt(i);
                pw.print(prefix);
                pw.print("* IntentBindRecord{");
                pw.print(Integer.toHexString(System.identityHashCode(b)));
                if ((b.collectFlags() & 1) != 0) {
                    pw.append(" CREATE");
                }
                pw.println("}:");
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(prefix);
                stringBuilder.append("  ");
                b.dumpInService(pw, stringBuilder.toString());
            }
        }
        if (this.connections.size() > 0) {
            pw.print(prefix);
            pw.println("All Connections:");
            for (i = 0; i < this.connections.size(); i++) {
                ArrayList<ConnectionRecord> c = (ArrayList) this.connections.valueAt(i);
                for (int i2 = 0; i2 < c.size(); i2++) {
                    pw.print(prefix);
                    pw.print("  ");
                    pw.println(c.get(i2));
                }
            }
        }
    }

    ServiceRecord(ActivityManagerService ams, Serv servStats, ComponentName name, FilterComparison intent, ServiceInfo sInfo, boolean callerIsFg, Runnable restarter) {
        this.ams = ams;
        this.stats = servStats;
        this.name = name;
        this.shortName = name.flattenToShortString();
        this.intent = intent;
        this.serviceInfo = sInfo;
        this.appInfo = sInfo.applicationInfo;
        this.packageName = sInfo.applicationInfo.packageName;
        this.processName = sInfo.processName;
        this.permission = sInfo.permission;
        this.exported = sInfo.exported;
        this.restarter = restarter;
        this.createRealTime = SystemClock.elapsedRealtime();
        this.lastActivity = SystemClock.uptimeMillis();
        this.userId = UserHandle.getUserId(this.appInfo.uid);
        this.createdFromFg = callerIsFg;
    }

    public ServiceState getTracker() {
        if (this.tracker != null) {
            return this.tracker;
        }
        if ((this.serviceInfo.applicationInfo.flags & 8) == 0) {
            this.tracker = this.ams.mProcessStats.getServiceStateLocked(this.serviceInfo.packageName, this.serviceInfo.applicationInfo.uid, (long) this.serviceInfo.applicationInfo.versionCode, this.serviceInfo.processName, this.serviceInfo.name);
            this.tracker.applyNewOwner(this);
        }
        return this.tracker;
    }

    public void forceClearTracker() {
        if (this.tracker != null) {
            this.tracker.clearCurrentOwner(this, true);
            this.tracker = null;
        }
    }

    public void makeRestarting(int memFactor, long now) {
        if (this.restartTracker == null) {
            if ((this.serviceInfo.applicationInfo.flags & 8) == 0) {
                this.restartTracker = this.ams.mProcessStats.getServiceStateLocked(this.serviceInfo.packageName, this.serviceInfo.applicationInfo.uid, (long) this.serviceInfo.applicationInfo.versionCode, this.serviceInfo.processName, this.serviceInfo.name);
            }
            if (this.restartTracker == null) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("makeRestarting restartTracker null for service ");
                stringBuilder.append(this.serviceInfo.name);
                Flog.w(102, stringBuilder.toString());
                return;
            }
        }
        this.restartTracker.setRestarting(true, memFactor, now);
    }

    public AppBindRecord retrieveAppBindingLocked(Intent intent, ProcessRecord app) {
        FilterComparison filter = new FilterComparison(intent);
        IntentBindRecord i = (IntentBindRecord) this.bindings.get(filter);
        if (i == null) {
            i = new IntentBindRecord(this, filter);
            this.bindings.put(filter, i);
        }
        AppBindRecord a = (AppBindRecord) i.apps.get(app);
        if (a != null) {
            return a;
        }
        a = new AppBindRecord(this, i, app);
        i.apps.put(app, a);
        return a;
    }

    public boolean hasAutoCreateConnections() {
        int conni = this.connections.size() - 1;
        while (true) {
            int i = 0;
            if (conni < 0) {
                return false;
            }
            ArrayList<ConnectionRecord> cr = (ArrayList) this.connections.valueAt(conni);
            while (i < cr.size()) {
                if ((((ConnectionRecord) cr.get(i)).flags & 1) != 0) {
                    return true;
                }
                i++;
            }
            conni--;
        }
    }

    public void updateWhitelistManager() {
        this.whitelistManager = false;
        for (int conni = this.connections.size() - 1; conni >= 0; conni--) {
            ArrayList<ConnectionRecord> cr = (ArrayList) this.connections.valueAt(conni);
            for (int i = 0; i < cr.size(); i++) {
                if ((((ConnectionRecord) cr.get(i)).flags & DumpState.DUMP_SERVICE_PERMISSIONS) != 0) {
                    this.whitelistManager = true;
                    return;
                }
            }
        }
    }

    public void resetRestartCounter() {
        this.restartCount = 0;
        this.restartDelay = 0;
        this.restartTime = 0;
    }

    public StartItem findDeliveredStart(int id, boolean taskRemoved, boolean remove) {
        int N = this.deliveredStarts.size();
        for (int i = 0; i < N; i++) {
            StartItem si = (StartItem) this.deliveredStarts.get(i);
            if (si.id == id && si.taskRemoved == taskRemoved) {
                if (remove) {
                    this.deliveredStarts.remove(i);
                }
                return si;
            }
        }
        return null;
    }

    public int getLastStartId() {
        return this.lastStartId;
    }

    public int makeNextStartId() {
        this.lastStartId++;
        if (this.lastStartId < 1) {
            this.lastStartId = 1;
        }
        return this.lastStartId;
    }

    public void postNotification() {
        int appUid = this.appInfo.uid;
        int appPid = this.app.pid;
        if (this.foregroundId != 0 && this.foregroundNoti != null) {
            String localPackageName = this.packageName;
            int localForegroundId = this.foregroundId;
            if (ActivityManagerDebugConfig.HWFLOW && localPackageName != null && localPackageName.equals(PackageManagerService.PLATFORM_PACKAGE_NAME)) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("postNotification  this ");
                stringBuilder.append(this);
                Slog.i("ActivityManager", stringBuilder.toString());
            }
            final Notification notification = this.foregroundNoti;
            final int i = appUid;
            final String str = localPackageName;
            final int i2 = appPid;
            final int i3 = localForegroundId;
            this.ams.mHandler.post(new Runnable() {
                /* JADX WARNING: Removed duplicated region for block: B:21:0x0114  */
                /* JADX WARNING: Removed duplicated region for block: B:33:0x0171 A:{Catch:{ RuntimeException -> 0x018c }} */
                /* JADX WARNING: Removed duplicated region for block: B:32:0x0158 A:{Catch:{ RuntimeException -> 0x018c }} */
                /* Code decompiled incorrectly, please refer to instructions dump. */
                public void run() {
                    RuntimeException e;
                    ActivityManagerService activityManagerService;
                    int i;
                    String str;
                    NotificationManagerInternal nm = (NotificationManagerInternal) LocalServices.getService(NotificationManagerInternal.class);
                    if (nm != null) {
                        Notification localForegroundNoti = notification;
                        Notification localForegroundNoti2;
                        int targetSdkVersion;
                        StringBuilder stringBuilder;
                        try {
                            if (localForegroundNoti.getSmallIcon() == null) {
                                CharSequence appName;
                                StringBuilder stringBuilder2 = new StringBuilder();
                                stringBuilder2.append("Attempted to start a foreground service (");
                                stringBuilder2.append(ServiceRecord.this.name);
                                stringBuilder2.append(") with a broken notification (no icon: ");
                                stringBuilder2.append(localForegroundNoti);
                                stringBuilder2.append(")");
                                Slog.v("ActivityManager", stringBuilder2.toString());
                                CharSequence appName2 = ServiceRecord.this.appInfo.loadLabel(ServiceRecord.this.ams.mContext.getPackageManager());
                                if (appName2 == null) {
                                    appName = ServiceRecord.this.appInfo.packageName;
                                } else {
                                    appName = appName2;
                                }
                                Context ctx = null;
                                try {
                                    Builder notiBuilder = new Builder(ServiceRecord.this.ams.mContext.createPackageContextAsUser(ServiceRecord.this.appInfo.packageName, 0, new UserHandle(UserHandle.getUserId(i))), localForegroundNoti.getChannelId());
                                    notiBuilder.setSmallIcon(ServiceRecord.this.appInfo.icon);
                                    notiBuilder.setFlag(64, true);
                                    Intent runningIntent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
                                    runningIntent.setData(Uri.fromParts("package", ServiceRecord.this.appInfo.packageName, null));
                                    PendingIntent pi = PendingIntent.getActivityAsUser(ServiceRecord.this.ams.mContext, 0, runningIntent, 134217728, null, UserHandle.of(ServiceRecord.this.userId));
                                    notiBuilder.setColor(ServiceRecord.this.ams.mContext.getColor(17170784));
                                    notiBuilder.setContentTitle(ServiceRecord.this.ams.mContext.getString(17039605, new Object[]{appName}));
                                    notiBuilder.setContentText(ServiceRecord.this.ams.mContext.getString(17039604, new Object[]{appName}));
                                    notiBuilder.setContentIntent(pi);
                                    localForegroundNoti2 = notiBuilder.build();
                                } catch (NameNotFoundException e2) {
                                }
                                if (nm.getNotificationChannel(str, i, localForegroundNoti2.getChannelId()) == null) {
                                    targetSdkVersion = 27;
                                    try {
                                        targetSdkVersion = ServiceRecord.this.ams.mContext.getPackageManager().getApplicationInfoAsUser(ServiceRecord.this.appInfo.packageName, 0, ServiceRecord.this.userId).targetSdkVersion;
                                    } catch (NameNotFoundException e3) {
                                    }
                                    if (targetSdkVersion >= 27) {
                                        stringBuilder = new StringBuilder();
                                        stringBuilder.append("invalid channel for service notification: ");
                                        stringBuilder.append(ServiceRecord.this.foregroundNoti);
                                        throw new RuntimeException(stringBuilder.toString());
                                    }
                                }
                                if (localForegroundNoti2.getSmallIcon() == null) {
                                    nm.enqueueNotification(str, str, i, i2, null, i3, localForegroundNoti2, ServiceRecord.this.userId);
                                    ServiceRecord.this.foregroundNoti = localForegroundNoti2;
                                }
                                stringBuilder = new StringBuilder();
                                stringBuilder.append("invalid service notification: ");
                                stringBuilder.append(ServiceRecord.this.foregroundNoti);
                                throw new RuntimeException(stringBuilder.toString());
                            }
                            localForegroundNoti2 = localForegroundNoti;
                            try {
                                if (nm.getNotificationChannel(str, i, localForegroundNoti2.getChannelId()) == null) {
                                }
                                if (localForegroundNoti2.getSmallIcon() == null) {
                                }
                            } catch (RuntimeException e4) {
                                e = e4;
                                Slog.w("ActivityManager", "Error showing notification for service", e);
                                ServiceRecord.this.ams.setServiceForeground(ServiceRecord.this.name, ServiceRecord.this, 0, null, 0);
                                activityManagerService = ServiceRecord.this.ams;
                                targetSdkVersion = i;
                                i = i2;
                                str = str;
                                stringBuilder = new StringBuilder();
                                stringBuilder.append("Bad notification for startForeground: ");
                                stringBuilder.append(e);
                                activityManagerService.crashApplication(targetSdkVersion, i, str, -1, stringBuilder.toString());
                            }
                        } catch (RuntimeException e5) {
                            e = e5;
                            localForegroundNoti2 = localForegroundNoti;
                            Slog.w("ActivityManager", "Error showing notification for service", e);
                            ServiceRecord.this.ams.setServiceForeground(ServiceRecord.this.name, ServiceRecord.this, 0, null, 0);
                            activityManagerService = ServiceRecord.this.ams;
                            targetSdkVersion = i;
                            i = i2;
                            str = str;
                            stringBuilder = new StringBuilder();
                            stringBuilder.append("Bad notification for startForeground: ");
                            stringBuilder.append(e);
                            activityManagerService.crashApplication(targetSdkVersion, i, str, -1, stringBuilder.toString());
                        }
                    }
                }
            });
        }
    }

    public void cancelNotification() {
        final String localPackageName = this.packageName;
        final int localForegroundId = this.foregroundId;
        this.ams.mHandler.post(new Runnable() {
            public void run() {
                INotificationManager inm = NotificationManager.getService();
                if (inm != null) {
                    try {
                        inm.cancelNotificationWithTag(localPackageName, null, localForegroundId, ServiceRecord.this.userId);
                    } catch (RuntimeException e) {
                        Slog.w("ActivityManager", "Error canceling notification for service", e);
                    } catch (RemoteException e2) {
                    }
                }
            }
        });
    }

    public void stripForegroundServiceFlagFromNotification() {
        if (this.foregroundId != 0) {
            final int localForegroundId = this.foregroundId;
            final int localUserId = this.userId;
            final String localPackageName = this.packageName;
            this.ams.mHandler.post(new Runnable() {
                public void run() {
                    NotificationManagerInternal nmi = (NotificationManagerInternal) LocalServices.getService(NotificationManagerInternal.class);
                    if (nmi != null) {
                        nmi.removeForegroundServiceFlagFromNotification(localPackageName, localForegroundId, localUserId);
                    }
                }
            });
        }
    }

    public void clearDeliveredStartsLocked() {
        for (int i = this.deliveredStarts.size() - 1; i >= 0; i--) {
            ((StartItem) this.deliveredStarts.get(i)).removeUriPermissionsLocked();
        }
        this.deliveredStarts.clear();
    }

    public String toString() {
        if (this.stringName != null) {
            return this.stringName;
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append("ServiceRecord{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        sb.append(" u");
        sb.append(this.userId);
        sb.append(' ');
        sb.append(this.shortName);
        sb.append('}');
        String stringBuilder = sb.toString();
        this.stringName = stringBuilder;
        return stringBuilder;
    }

    void updateApplicationInfo(ApplicationInfo aInfo) {
        this.appInfo = aInfo;
        this.serviceInfo.applicationInfo = aInfo;
    }

    public ComponentName getComponentName() {
        return this.name;
    }
}
