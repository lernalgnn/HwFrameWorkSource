package android.icu.impl;

import android.icu.util.ICUUncheckedIOException;
import android.icu.util.VersionInfo;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ICUBinary {
    static final /* synthetic */ boolean $assertionsDisabled = false;
    private static final byte CHAR_SET_ = (byte) 0;
    private static final byte CHAR_SIZE_ = (byte) 2;
    private static final String HEADER_AUTHENTICATION_FAILED_ = "ICU data file error: Header authentication failed, please check if you have a valid ICU data file";
    private static final byte MAGIC1 = (byte) -38;
    private static final byte MAGIC2 = (byte) 39;
    private static final String MAGIC_NUMBER_AUTHENTICATION_FAILED_ = "ICU data file error: Not an ICU data file";
    private static final List<DataFile> icuDataFiles = new ArrayList();

    public interface Authenticate {
        boolean isDataVersionAcceptable(byte[] bArr);
    }

    private static final class DatPackageReader {
        static final /* synthetic */ boolean $assertionsDisabled = false;
        private static final int DATA_FORMAT = 1131245124;
        private static final IsAcceptable IS_ACCEPTABLE = new IsAcceptable();

        private static final class IsAcceptable implements Authenticate {
            private IsAcceptable() {
            }

            public boolean isDataVersionAcceptable(byte[] version) {
                return version[0] == (byte) 1;
            }
        }

        static {
            Class cls = ICUBinary.class;
        }

        private DatPackageReader() {
        }

        static boolean validate(ByteBuffer bytes) {
            try {
                ICUBinary.readHeader(bytes, DATA_FORMAT, IS_ACCEPTABLE);
                int count = bytes.getInt(bytes.position());
                if (count > 0 && (bytes.position() + 4) + (count * 24) <= bytes.capacity() && startsWithPackageName(bytes, getNameOffset(bytes, 0)) && startsWithPackageName(bytes, getNameOffset(bytes, count - 1))) {
                    return true;
                }
                return false;
            } catch (IOException e) {
                return false;
            }
        }

        private static boolean startsWithPackageName(ByteBuffer bytes, int start) {
            int i;
            int length = "icudt60b".length() - 1;
            for (i = 0; i < length; i++) {
                if (bytes.get(start + i) != "icudt60b".charAt(i)) {
                    return false;
                }
            }
            i = length + 1;
            byte c = bytes.get(length + start);
            if ((c == (byte) 98 || c == (byte) 108) && bytes.get(start + i) == (byte) 47) {
                return true;
            }
            return false;
        }

        static ByteBuffer getData(ByteBuffer bytes, CharSequence key) {
            int index = binarySearch(bytes, key);
            if (index < 0) {
                return null;
            }
            ByteBuffer data = bytes.duplicate();
            data.position(getDataOffset(bytes, index));
            data.limit(getDataOffset(bytes, index + 1));
            return ICUBinary.sliceWithOrder(data);
        }

        static void addBaseNamesInFolder(ByteBuffer bytes, String folder, String suffix, Set<String> names) {
            int index = binarySearch(bytes, folder);
            if (index < 0) {
                index = ~index;
            }
            int count = bytes.getInt(bytes.position());
            StringBuilder sb = new StringBuilder();
            while (index < count && addBaseName(bytes, index, folder, suffix, sb, names)) {
                index++;
            }
        }

        private static int binarySearch(ByteBuffer bytes, CharSequence key) {
            int start = 0;
            int limit = bytes.getInt(bytes.position());
            while (start < limit) {
                int mid = (start + limit) >>> 1;
                int result = ICUBinary.compareKeys(key, bytes, getNameOffset(bytes, mid) + ("icudt60b".length() + 1));
                if (result < 0) {
                    limit = mid;
                } else if (result <= 0) {
                    return mid;
                } else {
                    start = mid + 1;
                }
            }
            return ~start;
        }

        private static int getNameOffset(ByteBuffer bytes, int index) {
            int base = bytes.position();
            return bytes.getInt((base + 4) + (index * 8)) + base;
        }

        private static int getDataOffset(ByteBuffer bytes, int index) {
            int base = bytes.position();
            if (index == bytes.getInt(base)) {
                return bytes.capacity();
            }
            return bytes.getInt(((base + 4) + 4) + (index * 8)) + base;
        }

        static boolean addBaseName(ByteBuffer bytes, int index, String folder, String suffix, StringBuilder sb, Set<String> names) {
            int offset;
            int offset2 = getNameOffset(bytes, index) + ("icudt60b".length() + 1);
            if (folder.length() != 0) {
                offset = offset2;
                offset2 = 0;
                while (offset2 < folder.length()) {
                    if (bytes.get(offset) != folder.charAt(offset2)) {
                        return false;
                    }
                    offset2++;
                    offset++;
                }
                offset2 = offset + 1;
                if (bytes.get(offset) != 47) {
                    return false;
                }
            }
            sb.setLength(0);
            while (true) {
                offset = offset2 + 1;
                byte b = bytes.get(offset2);
                byte b2 = b;
                if (b != (byte) 0) {
                    char c = (char) b2;
                    if (c == '/') {
                        return true;
                    }
                    sb.append(c);
                    offset2 = offset;
                } else {
                    offset2 = sb.length() - suffix.length();
                    if (sb.lastIndexOf(suffix, offset2) >= 0) {
                        names.add(sb.substring(0, offset2));
                    }
                    return true;
                }
            }
        }
    }

    private static abstract class DataFile {
        protected final String itemPath;

        abstract void addBaseNamesInFolder(String str, String str2, Set<String> set);

        abstract ByteBuffer getData(String str);

        DataFile(String item) {
            this.itemPath = item;
        }

        public String toString() {
            return this.itemPath;
        }
    }

    private static final class PackageDataFile extends DataFile {
        private final ByteBuffer pkgBytes;

        PackageDataFile(String item, ByteBuffer bytes) {
            super(item);
            this.pkgBytes = bytes;
        }

        ByteBuffer getData(String requestedPath) {
            return DatPackageReader.getData(this.pkgBytes, requestedPath);
        }

        void addBaseNamesInFolder(String folder, String suffix, Set<String> names) {
            DatPackageReader.addBaseNamesInFolder(this.pkgBytes, folder, suffix, names);
        }
    }

    private static final class SingleDataFile extends DataFile {
        private final File path;

        SingleDataFile(String item, File path) {
            super(item);
            this.path = path;
        }

        public String toString() {
            return this.path.toString();
        }

        ByteBuffer getData(String requestedPath) {
            if (requestedPath.equals(this.itemPath)) {
                return ICUBinary.mapFile(this.path);
            }
            return null;
        }

        void addBaseNamesInFolder(String folder, String suffix, Set<String> names) {
            if (this.itemPath.length() > folder.length() + suffix.length() && this.itemPath.startsWith(folder) && this.itemPath.endsWith(suffix) && this.itemPath.charAt(folder.length()) == '/' && this.itemPath.indexOf(47, folder.length() + 1) < 0) {
                names.add(this.itemPath.substring(folder.length() + 1, this.itemPath.length() - suffix.length()));
            }
        }
    }

    static {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(ICUBinary.class.getName());
        stringBuilder.append(".dataPath");
        String dataPath = ICUConfig.get(stringBuilder.toString());
        if (dataPath != null) {
            addDataFilesFromPath(dataPath, icuDataFiles);
        }
    }

    private static void addDataFilesFromPath(String dataPath, List<DataFile> list) {
        int pathStart = 0;
        while (pathStart < dataPath.length()) {
            int pathLimit;
            int sepIndex = dataPath.indexOf(File.pathSeparatorChar, pathStart);
            if (sepIndex >= 0) {
                pathLimit = sepIndex;
            } else {
                pathLimit = dataPath.length();
            }
            String path = dataPath.substring(pathStart, pathLimit).trim();
            if (path.endsWith(File.separator)) {
                path = path.substring(0, path.length() - 1);
            }
            if (path.length() != 0) {
                addDataFilesFromFolder(new File(path), new StringBuilder(), icuDataFiles);
            }
            if (sepIndex >= 0) {
                pathStart = sepIndex + 1;
            } else {
                return;
            }
        }
    }

    private static void addDataFilesFromFolder(File folder, StringBuilder itemPath, List<DataFile> dataFiles) {
        File[] files = folder.listFiles();
        if (files != null && files.length != 0) {
            int folderPathLength = itemPath.length();
            if (folderPathLength > 0) {
                itemPath.append('/');
                folderPathLength++;
            }
            for (File file : files) {
                String fileName = file.getName();
                if (!fileName.endsWith(".txt")) {
                    itemPath.append(fileName);
                    if (file.isDirectory()) {
                        addDataFilesFromFolder(file, itemPath, dataFiles);
                    } else if (fileName.endsWith(".dat")) {
                        ByteBuffer pkgBytes = mapFile(file);
                        if (pkgBytes != null && DatPackageReader.validate(pkgBytes)) {
                            dataFiles.add(new PackageDataFile(itemPath.toString(), pkgBytes));
                        }
                    } else {
                        dataFiles.add(new SingleDataFile(itemPath.toString(), file));
                    }
                    itemPath.setLength(folderPathLength);
                }
            }
        }
    }

    static int compareKeys(CharSequence key, ByteBuffer bytes, int offset) {
        int offset2 = offset;
        offset = 0;
        while (true) {
            int c2 = bytes.get(offset2);
            if (c2 == 0) {
                if (offset == key.length()) {
                    return 0;
                }
                return 1;
            } else if (offset == key.length()) {
                return -1;
            } else {
                int diff = key.charAt(offset) - c2;
                if (diff != 0) {
                    return diff;
                }
                offset++;
                offset2++;
            }
        }
    }

    static int compareKeys(CharSequence key, byte[] bytes, int offset) {
        int offset2 = offset;
        offset = 0;
        while (true) {
            int c2 = bytes[offset2];
            if (c2 == 0) {
                if (offset == key.length()) {
                    return 0;
                }
                return 1;
            } else if (offset == key.length()) {
                return -1;
            } else {
                int diff = key.charAt(offset) - c2;
                if (diff != 0) {
                    return diff;
                }
                offset++;
                offset2++;
            }
        }
    }

    public static ByteBuffer getData(String itemPath) {
        return getData(null, null, itemPath, false);
    }

    public static ByteBuffer getData(ClassLoader loader, String resourceName, String itemPath) {
        return getData(loader, resourceName, itemPath, false);
    }

    public static ByteBuffer getRequiredData(String itemPath) {
        return getData(null, null, itemPath, true);
    }

    private static ByteBuffer getData(ClassLoader loader, String resourceName, String itemPath, boolean required) {
        ByteBuffer bytes = getDataFromFile(itemPath);
        if (bytes != null) {
            return bytes;
        }
        if (loader == null) {
            loader = ClassLoaderUtil.getClassLoader(ICUData.class);
        }
        if (resourceName == null) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("android/icu/impl/data/icudt60b/");
            stringBuilder.append(itemPath);
            resourceName = stringBuilder.toString();
        }
        ByteBuffer buffer = null;
        try {
            InputStream is = ICUData.getStream(loader, resourceName, required);
            if (is == null) {
                return null;
            }
            return getByteBufferFromInputStreamAndCloseStream(is);
        } catch (IOException e) {
            throw new ICUUncheckedIOException(e);
        }
    }

    private static ByteBuffer getDataFromFile(String itemPath) {
        for (DataFile dataFile : icuDataFiles) {
            ByteBuffer data = dataFile.getData(itemPath);
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    private static ByteBuffer mapFile(File path) {
        FileInputStream file;
        try {
            file = new FileInputStream(path);
            FileChannel channel = file.getChannel();
            ByteBuffer bytes = null;
            ByteBuffer bytes2 = channel.map(MapMode.READ_ONLY, 0, channel.size());
            file.close();
            return bytes2;
        } catch (FileNotFoundException ignored) {
            System.err.println(ignored);
            return null;
        } catch (IOException ignored2) {
            System.err.println(ignored2);
            return null;
        } catch (Throwable th) {
            file.close();
        }
    }

    public static void addBaseNamesInFileFolder(String folder, String suffix, Set<String> names) {
        for (DataFile dataFile : icuDataFiles) {
            dataFile.addBaseNamesInFolder(folder, suffix, names);
        }
    }

    public static VersionInfo readHeaderAndDataVersion(ByteBuffer bytes, int dataFormat, Authenticate authenticate) throws IOException {
        return getVersionInfoFromCompactInt(readHeader(bytes, dataFormat, authenticate));
    }

    public static int readHeader(ByteBuffer bytes, int dataFormat, Authenticate authenticate) throws IOException {
        ByteBuffer byteBuffer = bytes;
        int i = dataFormat;
        Authenticate authenticate2 = authenticate;
        byte magic1 = byteBuffer.get(2);
        byte magic2 = byteBuffer.get(3);
        if (magic1 == MAGIC1 && magic2 == MAGIC2) {
            byte isBigEndian = byteBuffer.get(8);
            byte charsetFamily = byteBuffer.get((byte) 9);
            byte sizeofUChar = byteBuffer.get((byte) 10);
            if (isBigEndian < (byte) 0 || (byte) 1 < isBigEndian || charsetFamily != (byte) 0 || sizeofUChar != (byte) 2) {
                throw new IOException(HEADER_AUTHENTICATION_FAILED_);
            }
            byteBuffer.order(isBigEndian != (byte) 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
            int headerSize = byteBuffer.getChar(0);
            int sizeofUDataInfo = byteBuffer.getChar(4);
            if (sizeofUDataInfo < 20 || headerSize < sizeofUDataInfo + 4) {
                throw new IOException("Internal Error: Header size error");
            }
            byte[] formatVersion = new byte[]{byteBuffer.get(16), byteBuffer.get(17), byteBuffer.get(18), byteBuffer.get(19)};
            if (byteBuffer.get(12) == ((byte) (i >> 24)) && byteBuffer.get(13) == ((byte) (i >> 16)) && byteBuffer.get(14) == ((byte) (i >> 8)) && byteBuffer.get(15) == ((byte) i) && (authenticate2 == null || authenticate2.isDataVersionAcceptable(formatVersion))) {
                byteBuffer.position(headerSize);
                return (((byteBuffer.get(20) << 24) | ((byteBuffer.get(21) & 255) << 16)) | ((byteBuffer.get(22) & 255) << 8)) | (byteBuffer.get(23) & 255);
            }
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(HEADER_AUTHENTICATION_FAILED_);
            stringBuilder.append(String.format("; data format %02x%02x%02x%02x, format version %d.%d.%d.%d", new Object[]{Byte.valueOf(byteBuffer.get(12)), Byte.valueOf(byteBuffer.get(13)), Byte.valueOf(byteBuffer.get(14)), Byte.valueOf(byteBuffer.get(15)), Integer.valueOf(formatVersion[0] & 255), Integer.valueOf(formatVersion[1] & 255), Integer.valueOf(formatVersion[2] & 255), Integer.valueOf(formatVersion[3] & 255)}));
            throw new IOException(stringBuilder.toString());
        }
        throw new IOException(MAGIC_NUMBER_AUTHENTICATION_FAILED_);
    }

    public static int writeHeader(int dataFormat, int formatVersion, int dataVersion, DataOutputStream dos) throws IOException {
        dos.writeChar(32);
        dos.writeByte(-38);
        dos.writeByte(39);
        dos.writeChar(20);
        dos.writeChar(0);
        dos.writeByte(1);
        dos.writeByte(0);
        dos.writeByte(2);
        dos.writeByte(0);
        dos.writeInt(dataFormat);
        dos.writeInt(formatVersion);
        dos.writeInt(dataVersion);
        dos.writeLong(0);
        return 32;
    }

    public static void skipBytes(ByteBuffer bytes, int skipLength) {
        if (skipLength > 0) {
            bytes.position(bytes.position() + skipLength);
        }
    }

    public static String getString(ByteBuffer bytes, int length, int additionalSkipLength) {
        String s = bytes.asCharBuffer().subSequence(0, length).toString();
        skipBytes(bytes, (length * 2) + additionalSkipLength);
        return s;
    }

    public static char[] getChars(ByteBuffer bytes, int length, int additionalSkipLength) {
        char[] dest = new char[length];
        bytes.asCharBuffer().get(dest);
        skipBytes(bytes, (length * 2) + additionalSkipLength);
        return dest;
    }

    public static short[] getShorts(ByteBuffer bytes, int length, int additionalSkipLength) {
        short[] dest = new short[length];
        bytes.asShortBuffer().get(dest);
        skipBytes(bytes, (length * 2) + additionalSkipLength);
        return dest;
    }

    public static int[] getInts(ByteBuffer bytes, int length, int additionalSkipLength) {
        int[] dest = new int[length];
        bytes.asIntBuffer().get(dest);
        skipBytes(bytes, (length * 4) + additionalSkipLength);
        return dest;
    }

    public static long[] getLongs(ByteBuffer bytes, int length, int additionalSkipLength) {
        long[] dest = new long[length];
        bytes.asLongBuffer().get(dest);
        skipBytes(bytes, (length * 8) + additionalSkipLength);
        return dest;
    }

    public static ByteBuffer sliceWithOrder(ByteBuffer bytes) {
        return bytes.slice().order(bytes.order());
    }

    public static ByteBuffer getByteBufferFromInputStreamAndCloseStream(InputStream is) throws IOException {
        try {
            byte[] bytes;
            int avail = is.available();
            ByteBuffer byteBuffer = 128;
            if (avail > 32) {
                bytes = new byte[avail];
            } else {
                bytes = new byte[byteBuffer];
            }
            byte[] bytes2 = bytes;
            int length = 0;
            while (true) {
                int numRead;
                if (length < bytes2.length) {
                    numRead = is.read(bytes2, length, bytes2.length - length);
                    if (numRead < 0) {
                        break;
                    }
                    length += numRead;
                } else {
                    numRead = is.read();
                    if (numRead < 0) {
                        break;
                    }
                    int capacity = 2 * bytes2.length;
                    if (capacity < byteBuffer) {
                        capacity = 128;
                    } else if (capacity < 16384) {
                        capacity *= 2;
                    }
                    byte[] newBytes = new byte[capacity];
                    System.arraycopy(bytes2, 0, newBytes, 0, length);
                    bytes2 = newBytes;
                    int length2 = length + 1;
                    bytes2[length] = (byte) numRead;
                    length = length2;
                }
            }
            byteBuffer = ByteBuffer.wrap(bytes2, 0, length);
            return byteBuffer;
        } finally {
            is.close();
        }
    }

    public static VersionInfo getVersionInfoFromCompactInt(int version) {
        return VersionInfo.getInstance(version >>> 24, (version >> 16) & 255, (version >> 8) & 255, version & 255);
    }

    public static byte[] getVersionByteArrayFromCompactInt(int version) {
        return new byte[]{(byte) (version >> 24), (byte) (version >> 16), (byte) (version >> 8), (byte) version};
    }
}
