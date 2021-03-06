package com.android.server;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Binder;
import android.util.Log;
import com.huawei.attestation.IHwAttestationService.Stub;
import com.huawei.information.HwDeviceInfo;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

public class HwAttestationService extends Stub {
    private static final String EMMC_CONFIG_FILE = "/sys/block/mmcblk0/device/cid";
    private static final String HW_DEVICE_ATTESTATION_ACCESS = "com.huawei.deviceattestation.HW_DEVICE_ATTESTATION_ACCESS";
    private static int MAX_SING_DTAT_LEN = 512;
    private static int OPEN_REASE_MATCH_NUM = 0;
    private static final String PERMISSION_DEVICE_ATTESTATION = "com.huawei.permission.MANAGE_DEVICE_ATTESTATION";
    private static final String PERMISSION_DEVICE_READ_PHONE_STATE = "android.permission.READ_PHONE_STATE";
    private static final String TAG = "HwAttestationService";
    private int lastError = 0;
    private Context mContext;
    private HwSignature mSignTool;

    public HwAttestationService(Context context) {
        this.mContext = context;
        this.mSignTool = HwSignature.getInstance();
    }

    public int getLastError() {
        return this.lastError;
    }

    public byte[] getDeviceID(int deviceIdType) {
        if (!checkCallingPermission(Binder.getCallingUid()) && this.mContext.checkCallingPermission(PERMISSION_DEVICE_READ_PHONE_STATE) == -1) {
            Log.e(TAG, "permission denied");
            this.lastError = -5;
            return null;
        } else if (deviceIdType != 1) {
            return null;
        } else {
            byte[] emmcid = readEMMCIDByNative();
            if (emmcid.length > MAX_SING_DTAT_LEN) {
                return null;
            }
            return emmcid;
        }
    }

    public int getPublickKey(int keyIndex, byte[] keyBuf) {
        this.lastError = 0;
        if (keyIndex != 1) {
            String str = TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("not supprot keyIndex:");
            stringBuilder.append(keyIndex);
            Log.e(str, stringBuilder.toString());
            this.lastError = -4;
            return 0;
        } else if (keyBuf == null) {
            this.lastError = -4;
            return 0;
        } else {
            int actLen = this.mSignTool.getPublicKey(1, keyBuf);
            if (actLen <= 0) {
                this.lastError = -6;
            }
            return actLen;
        }
    }

    public byte[] getAttestationSignature(int keyIndex, int deviceIdType, String signatureType, byte[] challenge) {
        if (!checkCallingPermission(Binder.getCallingUid()) && this.mContext.checkCallingPermission(PERMISSION_DEVICE_READ_PHONE_STATE) == -1) {
            Log.e(TAG, "permission denied");
            this.lastError = -5;
            return null;
        } else if (deviceIdType == 1) {
            return getAttestationSignatureByEMMCId(keyIndex, signatureType, challenge);
        } else {
            return null;
        }
    }

    public byte[] getAttestationSignatureWithPkgName(int keyIndex, int deviceIdType, String signatureType, byte[] challenge, String packageName) {
        if (packageName == null) {
            this.lastError = -4;
            return null;
        } else if (!checkCallingPermission(packageName)) {
            Log.e(TAG, "permission denied");
            this.lastError = -5;
            return null;
        } else if (deviceIdType == 1) {
            return getAttestationSignatureByEMMCId(keyIndex, signatureType, challenge, packageName);
        } else {
            return null;
        }
    }

    public byte[] getAttestationSignatureByEMMCId(int keyIndex, String signatureType, byte[] challenge, String packageName) {
        String str = signatureType;
        byte[] bArr = challenge;
        String str2 = packageName;
        if (keyIndex != 1) {
            this.lastError = -4;
            return null;
        } else if (str == null || bArr == null || str2 == null) {
            this.lastError = -4;
            return null;
        } else if (str.getBytes(Charset.defaultCharset()).length > MAX_SING_DTAT_LEN || bArr.length > MAX_SING_DTAT_LEN) {
            this.lastError = -4;
            return null;
        } else {
            String[] pkgList = this.mContext.getPackageManager().getPackagesForUid(Binder.getCallingUid());
            String[] strArr;
            if (pkgList == null) {
            } else if (pkgList.length == 0) {
                strArr = pkgList;
            } else {
                boolean isCheckPkgName = false;
                for (String pkg : pkgList) {
                    if (str2.equals(pkg)) {
                        isCheckPkgName = true;
                        break;
                    }
                }
                if (!isCheckPkgName) {
                } else if (str2.getBytes(Charset.defaultCharset()).length > MAX_SING_DTAT_LEN) {
                    strArr = pkgList;
                } else {
                    byte[] emmcid = readEMMCIDByNative();
                    if (emmcid.length > MAX_SING_DTAT_LEN) {
                        this.lastError = -3;
                        return null;
                    } else if (this.mSignTool.checkKeyStatus(1) != 0) {
                        this.lastError = -2;
                        return null;
                    } else {
                        this.lastError = 0;
                        return this.mSignTool.signMessage(1, str2.getBytes(Charset.defaultCharset()), str2.getBytes(Charset.defaultCharset()).length, emmcid, emmcid.length, str.getBytes(Charset.defaultCharset()), str.getBytes(Charset.defaultCharset()).length, bArr, bArr.length);
                    }
                }
                this.lastError = -4;
                return null;
            }
            this.lastError = -4;
            return null;
        }
    }

    public byte[] getAttestationSignatureByEMMCId(int keyIndex, String signatureType, byte[] challenge) {
        String str = signatureType;
        byte[] bArr = challenge;
        if (keyIndex != 1) {
            this.lastError = -4;
            return null;
        } else if (str == null || bArr == null) {
            this.lastError = -4;
            return null;
        } else if (str.getBytes(Charset.defaultCharset()).length > MAX_SING_DTAT_LEN || bArr.length > MAX_SING_DTAT_LEN) {
            this.lastError = -4;
            return null;
        } else {
            String pkgName = getPackageName(Binder.getCallingUid());
            if (pkgName == null) {
                this.lastError = -4;
                return null;
            } else if (pkgName.getBytes(Charset.defaultCharset()).length > MAX_SING_DTAT_LEN) {
                this.lastError = -4;
                return null;
            } else {
                byte[] emmcid = readEMMCIDByNative();
                if (emmcid.length > MAX_SING_DTAT_LEN) {
                    this.lastError = -3;
                    return null;
                } else if (this.mSignTool.checkKeyStatus(1) != 0) {
                    this.lastError = -2;
                    return null;
                } else {
                    this.lastError = 0;
                    return this.mSignTool.signMessage(1, pkgName.getBytes(Charset.defaultCharset()), pkgName.getBytes(Charset.defaultCharset()).length, emmcid, emmcid.length, str.getBytes(Charset.defaultCharset()), str.getBytes(Charset.defaultCharset()).length, bArr, bArr.length);
                }
            }
        }
    }

    private String getPackageName(int uid) {
        String[] pkg = this.mContext.getPackageManager().getPackagesForUid(uid);
        if (pkg == null || pkg.length == 0) {
            return null;
        }
        return pkg[0];
    }

    private boolean checkCallingPermission(int uid) {
        PackageManager pm = this.mContext.getPackageManager();
        if (pm == null) {
            return false;
        }
        try {
            boolean z = false;
            if ((pm.getApplicationInfo(getPackageName(uid), 0).flags & 1) != 0) {
                z = true;
            }
            boolean isCheckPermission = z;
            if (isCheckPermission || pm.checkSignatures(getPackageName(uid), "android") < 0) {
                return isCheckPermission;
            }
            return true;
        } catch (NameNotFoundException e) {
            Log.e(TAG, "NameNotFoundException in HwAttestationService.checkCallingPermission()", e);
            return false;
        }
    }

    private boolean checkCallingPermission(String pkgName) {
        PackageManager pm = this.mContext.getPackageManager();
        if (pm == null) {
            return false;
        }
        boolean z = false;
        try {
            if ((pm.getApplicationInfo(pkgName, 0).flags & 1) != 0) {
                z = true;
            }
            boolean isCheckPermission = z;
            if (isCheckPermission || pm.checkSignatures(pkgName, "android") < 0) {
                return isCheckPermission;
            }
            return true;
        } catch (NameNotFoundException e) {
            Log.e(TAG, "NameNotFoundException in HwAttestationService.checkCallingPermission()", e);
            return false;
        }
    }

    private byte[] readEMMCID() throws IOException {
        BufferedReader br = null;
        StringBuffer sb = null;
        try {
            File file = new File(EMMC_CONFIG_FILE);
            if (!file.exists() || file.isDirectory()) {
                throw new FileNotFoundException();
            }
            br = new BufferedReader(new FileReader(file));
            sb = new StringBuffer();
            for (String temp = br.readLine(); temp != null; temp = br.readLine()) {
                sb.append(temp);
            }
            try {
                br.close();
            } catch (IOException file2) {
                Log.e(TAG, "IOException in HwAttestationService.readEMMCID()", file2);
            }
            if (sb != null) {
                return sb.toString().getBytes(Charset.defaultCharset());
            }
            return null;
        } catch (IOException e) {
            Log.e(TAG, "IOException in HwAttestationService.readEMMCID()", e);
            if (br != null) {
                br.close();
            }
        } catch (Throwable th) {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e2) {
                    Log.e(TAG, "IOException in HwAttestationService.readEMMCID()", e2);
                }
            }
        }
    }

    private byte[] readEMMCIDByNative() {
        String emmcid = HwDeviceInfo.getEMMCID();
        if (emmcid != null) {
            try {
                return emmcid.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return new byte[0];
    }

    public int getDeviceCertType(int keyIndex) {
        this.lastError = 0;
        if (this.mContext.checkCallingPermission(PERMISSION_DEVICE_ATTESTATION) == -1) {
            Log.e(TAG, "permission denied");
            this.lastError = -5;
            return -1;
        } else if (keyIndex != 1) {
            String str = TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("not supprot keyIndex:");
            stringBuilder.append(keyIndex);
            Log.e(str, stringBuilder.toString());
            this.lastError = -4;
            return -1;
        } else {
            int certType = this.mSignTool.getDeviceCertType(keyIndex);
            if (certType < 0) {
                this.lastError = -7;
            }
            return certType;
        }
    }

    public int getDeviceCert(int keyIndex, int certType, byte[] certBuf) {
        this.lastError = 0;
        String str;
        if (this.mContext.checkCallingPermission(PERMISSION_DEVICE_ATTESTATION) == -1) {
            Log.e(TAG, "permission denied");
            this.lastError = -5;
            return -1;
        } else if (keyIndex != 1) {
            str = TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("not supprot keyIndex:");
            stringBuilder.append(keyIndex);
            Log.e(str, stringBuilder.toString());
            this.lastError = -4;
            return -1;
        } else if (certBuf == null) {
            Log.e(TAG, "certBuf is null");
            this.lastError = -4;
            return -1;
        } else {
            int certLen = this.mSignTool.getDeviceCert(keyIndex, certType, certBuf);
            if (certLen <= 0) {
                str = TAG;
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append("getDeviceCert get cert fail: ");
                stringBuilder2.append(certLen);
                Log.e(str, stringBuilder2.toString());
                this.lastError = -8;
            }
            return certLen;
        }
    }
}
