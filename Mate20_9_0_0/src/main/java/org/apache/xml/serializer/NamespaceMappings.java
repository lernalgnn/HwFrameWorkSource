package org.apache.xml.serializer;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import org.apache.xalan.templates.Constants;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class NamespaceMappings {
    private static final String EMPTYSTRING = "";
    private static final String XML_PREFIX = "xml";
    private int count = 0;
    private Hashtable m_namespaces = new Hashtable();
    private Stack m_nodeStack = new Stack();

    class MappingRecord {
        final int m_declarationDepth;
        final String m_prefix;
        final String m_uri;

        MappingRecord(String prefix, String uri, int depth) {
            this.m_prefix = prefix;
            this.m_uri = uri == null ? "" : uri;
            this.m_declarationDepth = depth;
        }
    }

    private class Stack {
        Object[] m_stack = new Object[this.max];
        private int max = 20;
        private int top = -1;

        public Object clone() throws CloneNotSupportedException {
            Stack clone = new Stack();
            clone.max = this.max;
            clone.top = this.top;
            clone.m_stack = new Object[clone.max];
            for (int i = 0; i <= this.top; i++) {
                clone.m_stack[i] = this.m_stack[i];
            }
            return clone;
        }

        public Object push(Object o) {
            this.top++;
            if (this.max <= this.top) {
                int newMax = (2 * this.max) + 1;
                Object[] newArray = new Object[newMax];
                System.arraycopy(this.m_stack, 0, newArray, 0, this.max);
                this.max = newMax;
                this.m_stack = newArray;
            }
            this.m_stack[this.top] = o;
            return o;
        }

        public Object pop() {
            if (this.top < 0) {
                return null;
            }
            Object o = this.m_stack[this.top];
            this.top--;
            return o;
        }

        public Object peek() {
            if (this.top >= 0) {
                return this.m_stack[this.top];
            }
            return null;
        }

        public Object peek(int idx) {
            return this.m_stack[idx];
        }

        public boolean isEmpty() {
            return this.top < 0;
        }

        public boolean empty() {
            return this.top < 0;
        }

        public void clear() {
            for (int i = 0; i <= this.top; i++) {
                this.m_stack[i] = null;
            }
            this.top = -1;
        }

        public Object getElement(int index) {
            return this.m_stack[index];
        }
    }

    public NamespaceMappings() {
        initNamespaces();
    }

    private void initNamespaces() {
        createPrefixStack("").push(new MappingRecord("", "", -1));
        createPrefixStack("xml").push(new MappingRecord("xml", "http://www.w3.org/XML/1998/namespace", -1));
    }

    public String lookupNamespace(String prefix) {
        String uri = null;
        Stack stack = getPrefixStack(prefix);
        if (!(stack == null || stack.isEmpty())) {
            uri = ((MappingRecord) stack.peek()).m_uri;
        }
        if (uri == null) {
            return "";
        }
        return uri;
    }

    MappingRecord getMappingFromPrefix(String prefix) {
        Stack stack = (Stack) this.m_namespaces.get(prefix);
        return (stack == null || stack.isEmpty()) ? null : (MappingRecord) stack.peek();
    }

    public String lookupPrefix(String uri) {
        Enumeration prefixes = this.m_namespaces.keys();
        while (prefixes.hasMoreElements()) {
            String prefix = (String) prefixes.nextElement();
            String uri2 = lookupNamespace(prefix);
            if (uri2 != null && uri2.equals(uri)) {
                return prefix;
            }
        }
        return null;
    }

    MappingRecord getMappingFromURI(String uri) {
        Enumeration prefixes = this.m_namespaces.keys();
        while (prefixes.hasMoreElements()) {
            MappingRecord map2 = getMappingFromPrefix((String) prefixes.nextElement());
            if (map2 != null && map2.m_uri.equals(uri)) {
                return map2;
            }
        }
        return null;
    }

    boolean popNamespace(String prefix) {
        if (prefix.startsWith("xml")) {
            return false;
        }
        Stack prefixStack = getPrefixStack(prefix);
        Stack stack = prefixStack;
        if (prefixStack == null) {
            return false;
        }
        stack.pop();
        return true;
    }

    public boolean pushNamespace(String prefix, String uri, int elemDepth) {
        if (prefix.startsWith("xml")) {
            return false;
        }
        MappingRecord mr;
        Stack stack = (Stack) this.m_namespaces.get(prefix);
        Stack stack2 = stack;
        if (stack == null) {
            Hashtable hashtable = this.m_namespaces;
            Stack stack3 = new Stack();
            stack2 = stack3;
            hashtable.put(prefix, stack3);
        }
        if (!stack2.empty()) {
            mr = (MappingRecord) stack2.peek();
            if (uri.equals(mr.m_uri) || elemDepth == mr.m_declarationDepth) {
                return false;
            }
        }
        mr = new MappingRecord(prefix, uri, elemDepth);
        stack2.push(mr);
        this.m_nodeStack.push(mr);
        return true;
    }

    void popNamespaces(int elemDepth, ContentHandler saxHandler) {
        while (!this.m_nodeStack.isEmpty()) {
            MappingRecord map = (MappingRecord) this.m_nodeStack.peek();
            int depth = map.m_declarationDepth;
            if (elemDepth >= 1 && map.m_declarationDepth >= elemDepth) {
                MappingRecord nm1 = (MappingRecord) this.m_nodeStack.pop();
                String prefix = map.m_prefix;
                Stack prefixStack = getPrefixStack(prefix);
                if (nm1 == ((MappingRecord) prefixStack.peek())) {
                    prefixStack.pop();
                    if (saxHandler != null) {
                        try {
                            saxHandler.endPrefixMapping(prefix);
                        } catch (SAXException e) {
                        }
                    }
                }
            } else {
                return;
            }
        }
    }

    public String generateNextPrefix() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(Constants.ATTRNAME_NS);
        int i = this.count;
        this.count = i + 1;
        stringBuilder.append(i);
        return stringBuilder.toString();
    }

    public Object clone() throws CloneNotSupportedException {
        NamespaceMappings clone = new NamespaceMappings();
        clone.m_nodeStack = (Stack) this.m_nodeStack.clone();
        clone.count = this.count;
        clone.m_namespaces = (Hashtable) this.m_namespaces.clone();
        clone.count = this.count;
        return clone;
    }

    final void reset() {
        this.count = 0;
        this.m_namespaces.clear();
        this.m_nodeStack.clear();
        initNamespaces();
    }

    private Stack getPrefixStack(String prefix) {
        return (Stack) this.m_namespaces.get(prefix);
    }

    private Stack createPrefixStack(String prefix) {
        Stack fs = new Stack();
        this.m_namespaces.put(prefix, fs);
        return fs;
    }

    public String[] lookupAllPrefixes(String uri) {
        ArrayList foundPrefixes = new ArrayList();
        Enumeration prefixes = this.m_namespaces.keys();
        while (prefixes.hasMoreElements()) {
            String prefix = (String) prefixes.nextElement();
            String uri2 = lookupNamespace(prefix);
            if (uri2 != null && uri2.equals(uri)) {
                foundPrefixes.add(prefix);
            }
        }
        String[] prefixArray = new String[foundPrefixes.size()];
        foundPrefixes.toArray(prefixArray);
        return prefixArray;
    }
}
