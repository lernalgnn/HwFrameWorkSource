package gov.nist.javax.sip.header;

import gov.nist.core.NameValue;
import gov.nist.core.Separators;
import java.text.ParseException;
import javax.sip.address.URI;

public abstract class AuthenticationHeader extends ParametersHeader {
    public static final String ALGORITHM = "algorithm";
    public static final String CK = "ck";
    public static final String CNONCE = "cnonce";
    public static final String DOMAIN = "domain";
    public static final String IK = "ik";
    public static final String INTEGRITY_PROTECTED = "integrity-protected";
    public static final String NC = "nc";
    public static final String NONCE = "nonce";
    public static final String OPAQUE = "opaque";
    public static final String QOP = "qop";
    public static final String REALM = "realm";
    public static final String RESPONSE = "response";
    public static final String SIGNATURE = "signature";
    public static final String SIGNED_BY = "signed-by";
    public static final String STALE = "stale";
    public static final String URI = "uri";
    public static final String USERNAME = "username";
    protected String scheme;

    public AuthenticationHeader(String name) {
        super(name);
        this.parameters.setSeparator(Separators.COMMA);
        this.scheme = ParameterNames.DIGEST;
    }

    public AuthenticationHeader() {
        this.parameters.setSeparator(Separators.COMMA);
    }

    public void setParameter(String name, String value) throws ParseException {
        NameValue nv = this.parameters.getNameValue(name.toLowerCase());
        if (nv == null) {
            nv = new NameValue(name, value);
            if (name.equalsIgnoreCase("qop") || name.equalsIgnoreCase("realm") || name.equalsIgnoreCase("cnonce") || name.equalsIgnoreCase("nonce") || name.equalsIgnoreCase("username") || name.equalsIgnoreCase("domain") || name.equalsIgnoreCase("opaque") || name.equalsIgnoreCase(ParameterNames.NEXT_NONCE) || name.equalsIgnoreCase("uri") || name.equalsIgnoreCase("response") || name.equalsIgnoreCase("ik") || name.equalsIgnoreCase("ck") || name.equalsIgnoreCase("integrity-protected")) {
                if (!(((this instanceof Authorization) || (this instanceof ProxyAuthorization)) && name.equalsIgnoreCase("qop"))) {
                    nv.setQuotedValue();
                }
                if (value == null) {
                    throw new NullPointerException("null value");
                } else if (value.startsWith(Separators.DOUBLE_QUOTE)) {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(value);
                    stringBuilder.append(" : Unexpected DOUBLE_QUOTE");
                    throw new ParseException(stringBuilder.toString(), 0);
                }
            }
            super.setParameter(nv);
            return;
        }
        nv.setValueAsObject(value);
    }

    public void setChallenge(Challenge challenge) {
        this.scheme = challenge.scheme;
        this.parameters = challenge.authParams;
    }

    public String encodeBody() {
        this.parameters.setSeparator(Separators.COMMA);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(this.scheme);
        stringBuilder.append(Separators.SP);
        stringBuilder.append(this.parameters.encode());
        return stringBuilder.toString();
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getScheme() {
        return this.scheme;
    }

    public void setRealm(String realm) throws ParseException {
        if (realm != null) {
            setParameter("realm", realm);
            return;
        }
        throw new NullPointerException("JAIN-SIP Exception,  AuthenticationHeader, setRealm(), The realm parameter is null");
    }

    public String getRealm() {
        return getParameter("realm");
    }

    public void setNonce(String nonce) throws ParseException {
        if (nonce != null) {
            setParameter("nonce", nonce);
            return;
        }
        throw new NullPointerException("JAIN-SIP Exception,  AuthenticationHeader, setNonce(), The nonce parameter is null");
    }

    public String getNonce() {
        return getParameter("nonce");
    }

    public void setURI(URI uri) {
        if (uri != null) {
            NameValue nv = new NameValue("uri", uri);
            nv.setQuotedValue();
            this.parameters.set(nv);
            return;
        }
        throw new NullPointerException("Null URI");
    }

    public URI getURI() {
        return getParameterAsURI("uri");
    }

    public void setAlgorithm(String algorithm) throws ParseException {
        if (algorithm != null) {
            setParameter("algorithm", algorithm);
            return;
        }
        throw new NullPointerException("null arg");
    }

    public String getAlgorithm() {
        return getParameter("algorithm");
    }

    public void setQop(String qop) throws ParseException {
        if (qop != null) {
            setParameter("qop", qop);
            return;
        }
        throw new NullPointerException("null arg");
    }

    public String getQop() {
        return getParameter("qop");
    }

    public void setOpaque(String opaque) throws ParseException {
        if (opaque != null) {
            setParameter("opaque", opaque);
            return;
        }
        throw new NullPointerException("null arg");
    }

    public String getOpaque() {
        return getParameter("opaque");
    }

    public void setDomain(String domain) throws ParseException {
        if (domain != null) {
            setParameter("domain", domain);
            return;
        }
        throw new NullPointerException("null arg");
    }

    public String getDomain() {
        return getParameter("domain");
    }

    public void setStale(boolean stale) {
        setParameter(new NameValue("stale", Boolean.valueOf(stale)));
    }

    public boolean isStale() {
        return getParameterAsBoolean("stale");
    }

    public void setCNonce(String cnonce) throws ParseException {
        setParameter("cnonce", cnonce);
    }

    public String getCNonce() {
        return getParameter("cnonce");
    }

    public int getNonceCount() {
        return getParameterAsHexInt("nc");
    }

    public void setNonceCount(int param) throws ParseException {
        if (param >= 0) {
            String nc = Integer.toHexString(param);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("00000000".substring(0, 8 - nc.length()));
            stringBuilder.append(nc);
            setParameter("nc", stringBuilder.toString());
            return;
        }
        throw new ParseException("bad value", 0);
    }

    public String getResponse() {
        return (String) getParameterValue("response");
    }

    public void setResponse(String response) throws ParseException {
        if (response != null) {
            setParameter("response", response);
            return;
        }
        throw new NullPointerException("Null parameter");
    }

    public String getUsername() {
        return getParameter("username");
    }

    public void setUsername(String username) throws ParseException {
        setParameter("username", username);
    }

    public void setIK(String ik) throws ParseException {
        if (ik != null) {
            setParameter("ik", ik);
            return;
        }
        throw new NullPointerException("JAIN-SIP Exception,  AuthenticationHeader, setIk(), The auth-param IK parameter is null");
    }

    public String getIK() {
        return getParameter("ik");
    }

    public void setCK(String ck) throws ParseException {
        if (ck != null) {
            setParameter("ck", ck);
            return;
        }
        throw new NullPointerException("JAIN-SIP Exception,  AuthenticationHeader, setCk(), The auth-param CK parameter is null");
    }

    public String getCK() {
        return getParameter("ck");
    }

    public void setIntegrityProtected(String integrityProtected) throws ParseException {
        if (integrityProtected != null) {
            setParameter("integrity-protected", integrityProtected);
            return;
        }
        throw new NullPointerException("JAIN-SIP Exception,  AuthenticationHeader, setIntegrityProtected(), The integrity-protected parameter is null");
    }

    public String getIntegrityProtected() {
        return getParameter("integrity-protected");
    }
}
