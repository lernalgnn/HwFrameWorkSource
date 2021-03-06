package android.net.netlink;

import android.system.OsConstants;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Enumeration;

public class RtNetlinkMessage extends NetlinkMessage {
    public static final short RTA_DST = (short) 1;
    public static final short RTA_GATEWAY = (short) 5;
    public static final short RTA_IIF = (short) 3;
    public static final short RTA_MARK = (short) 16;
    public static final short RTA_OIF = (short) 4;
    public static final short RTA_PREFSRC = (short) 7;
    public static final short RTA_SRC = (short) 2;
    public static final short RTA_TABLE = (short) 15;
    public static final short RTA_UNSPEC = (short) 0;
    public static final short RTA_VIA = (short) 18;
    public static final short RTN_BROADCAST = (short) 3;
    public static final short RTN_LOCAL = (short) 2;
    public static final short RTN_UNICAST = (short) 1;
    private static final String TAG = "RtNetlinkMessage";
    private static final int WIFI_IPV4_HOST_LEN = 32;
    private static final int WIFI_IPV6_HOST_LEN = 128;
    private StructNlAttr mDest = null;
    private StructNlAttr mGateway = null;
    private StructNlAttr mOif = null;
    private StructNlAttr mPrefSrc = null;
    private StructRtMsg mRtMsg = null;
    private StructNlAttr mVia = null;

    public static class StructRtMsg {
        public static final int STRUCT_SIZE = 12;
        byte rtm_dst_len;
        byte rtm_family;
        int rtm_flags;
        byte rtm_protocol;
        byte rtm_scope;
        byte rtm_src_len;
        byte rtm_table;
        byte rtm_tos;
        byte rtm_type;

        public static boolean hasAvailableSpace(ByteBuffer byteBuffer) {
            return byteBuffer != null && byteBuffer.remaining() >= 12;
        }

        public static StructRtMsg parse(ByteBuffer byteBuffer) {
            if (!hasAvailableSpace(byteBuffer)) {
                return null;
            }
            StructRtMsg struct = new StructRtMsg();
            struct.rtm_family = byteBuffer.get();
            struct.rtm_dst_len = byteBuffer.get();
            struct.rtm_src_len = byteBuffer.get();
            struct.rtm_tos = byteBuffer.get();
            struct.rtm_table = byteBuffer.get();
            struct.rtm_protocol = byteBuffer.get();
            struct.rtm_scope = byteBuffer.get();
            struct.rtm_type = byteBuffer.get();
            struct.rtm_flags = byteBuffer.getInt();
            return struct;
        }

        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("StructRtMsg{ rtm_family{");
            stringBuilder.append(this.rtm_family);
            stringBuilder.append("}, rtm_dst_len{");
            stringBuilder.append(this.rtm_dst_len);
            stringBuilder.append("}, rtm_src_len{");
            stringBuilder.append(this.rtm_src_len);
            stringBuilder.append("}, rtm_tos{");
            stringBuilder.append(this.rtm_tos);
            stringBuilder.append("}, rtm_table{");
            stringBuilder.append(this.rtm_table);
            stringBuilder.append("}, rtm_protocol{");
            stringBuilder.append(this.rtm_protocol);
            stringBuilder.append("}, rtm_scope{");
            stringBuilder.append(this.rtm_scope);
            stringBuilder.append("}, rtm_flags{");
            stringBuilder.append(this.rtm_flags);
            stringBuilder.append("}, }");
            return stringBuilder.toString();
        }
    }

    private static StructNlAttr findNextAttrOfType(short attrType, ByteBuffer byteBuffer) {
        while (byteBuffer != null && byteBuffer.remaining() > 0) {
            StructNlAttr nlAttr = StructNlAttr.peek(byteBuffer);
            if (nlAttr == null) {
                break;
            } else if (nlAttr.nla_type == attrType) {
                return StructNlAttr.parse(byteBuffer);
            } else {
                if (byteBuffer.remaining() < nlAttr.getAlignedLength()) {
                    break;
                }
                byteBuffer.position(byteBuffer.position() + nlAttr.getAlignedLength());
            }
        }
        return null;
    }

    private static String getInterfaceNameByIndex(int index) {
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                NetworkInterface ifc = (NetworkInterface) en.nextElement();
                if (index == ifc.getIndex()) {
                    return ifc.getName();
                }
            }
            return null;
        } catch (SocketException e) {
            return null;
        }
    }

    public static RtNetlinkMessage parse(StructNlMsgHdr header, ByteBuffer byteBuffer) {
        RtNetlinkMessage neighMsg = new RtNetlinkMessage(header);
        neighMsg.mRtMsg = StructRtMsg.parse(byteBuffer);
        if (neighMsg.mRtMsg == null) {
            return null;
        }
        int baseOffset = byteBuffer.position();
        StructNlAttr nlAttr = findNextAttrOfType((short) 1, byteBuffer);
        if (nlAttr != null) {
            neighMsg.mDest = nlAttr;
        }
        byteBuffer.position(baseOffset);
        nlAttr = findNextAttrOfType((short) 5, byteBuffer);
        if (nlAttr != null) {
            neighMsg.mGateway = nlAttr;
        }
        byteBuffer.position(baseOffset);
        nlAttr = findNextAttrOfType((short) 18, byteBuffer);
        if (nlAttr != null) {
            neighMsg.mVia = nlAttr;
        }
        byteBuffer.position(baseOffset);
        nlAttr = findNextAttrOfType((short) 4, byteBuffer);
        if (nlAttr != null) {
            neighMsg.mOif = nlAttr;
        }
        byteBuffer.position(baseOffset);
        nlAttr = findNextAttrOfType((short) 7, byteBuffer);
        if (nlAttr != null) {
            neighMsg.mPrefSrc = nlAttr;
        }
        byteBuffer.position(baseOffset);
        int kAdditionalSpace = NetlinkConstants.alignedLengthOf(neighMsg.mHeader.nlmsg_len - 28);
        if (byteBuffer.remaining() < kAdditionalSpace) {
            byteBuffer.position(byteBuffer.limit());
        } else {
            byteBuffer.position(baseOffset + kAdditionalSpace);
        }
        return neighMsg;
    }

    public static byte[] newNewGetRouteMessage() {
        byte[] bytes = new byte[28];
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.nativeOrder());
        StructNlMsgHdr nlmsghdr = new StructNlMsgHdr();
        nlmsghdr.nlmsg_len = 28;
        nlmsghdr.nlmsg_type = (short) 26;
        nlmsghdr.nlmsg_flags = (short) 769;
        nlmsghdr.pack(byteBuffer);
        new StructNdMsg().pack(byteBuffer);
        return bytes;
    }

    private RtNetlinkMessage(StructNlMsgHdr header) {
        super(header);
    }

    int getHostLen(int af) {
        if (af == OsConstants.AF_INET6) {
            return 128;
        }
        if (af == OsConstants.AF_INET) {
            return 32;
        }
        return 0;
    }

    String getRtnType() {
        if (this.mRtMsg.rtm_type != (byte) 1) {
            if (this.mRtMsg.rtm_type == (byte) 2) {
                return "local ";
            }
            if (this.mRtMsg.rtm_type == (byte) 3) {
                return "broadcast ";
            }
        }
        return BackupManagerConstants.DEFAULT_BACKUP_FINISHED_NOTIFICATION_RECEIVERS;
    }

    String getDest() {
        byte hostLen = getHostLen(this.mRtMsg.rtm_family);
        if (this.mDest != null) {
            InetAddress address = this.mDest.getValueAsInetAddress();
            if (this.mRtMsg.rtm_dst_len != hostLen) {
                return String.format("%s/%d ", new Object[]{address.toString(), Byte.valueOf(this.mRtMsg.rtm_dst_len)});
            }
            return String.format("%s ", new Object[]{address.toString()});
        } else if (this.mRtMsg.rtm_dst_len == (byte) 0) {
            return "default ";
        } else {
            return String.format("0/%d ", new Object[]{Byte.valueOf(this.mRtMsg.rtm_dst_len)});
        }
    }

    String getVia() {
        if (this.mGateway != null) {
            return String.format("via %s ", new Object[]{this.mGateway.getValueAsInetAddress().toString()});
        } else if (this.mVia == null) {
            return BackupManagerConstants.DEFAULT_BACKUP_FINISHED_NOTIFICATION_RECEIVERS;
        } else {
            return String.format("via %s ", new Object[]{this.mVia.getValueAsInetAddress().toString()});
        }
    }

    String getdev() {
        if (this.mOif == null || this.mOif.getValueAsInt(-1) == -1) {
            return BackupManagerConstants.DEFAULT_BACKUP_FINISHED_NOTIFICATION_RECEIVERS;
        }
        return String.format("dev %s ", new Object[]{getInterfaceNameByIndex(this.mOif.getValueAsInt(-1))});
    }

    String getPrefSrc() {
        if (this.mPrefSrc == null) {
            return BackupManagerConstants.DEFAULT_BACKUP_FINISHED_NOTIFICATION_RECEIVERS;
        }
        return String.format("src %s ", new Object[]{this.mPrefSrc.getValueAsInetAddress().toString()});
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(getRtnType());
        stringBuilder.append(getDest());
        stringBuilder.append(getVia());
        stringBuilder.append(getdev());
        stringBuilder.append(getPrefSrc());
        stringBuilder.append("\n");
        return stringBuilder.toString();
    }
}
