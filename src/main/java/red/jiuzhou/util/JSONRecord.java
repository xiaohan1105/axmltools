package red.jiuzhou.util;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ui.Dbxmltool;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;

public class JSONRecord extends JSONObject {

    private static final Logger log = LoggerFactory.getLogger(JSONRecord.class);
    boolean isXml = false;
    public static final char NESTED_DELIM = ':';

    public boolean isXml() {
        return isXml;
    }

    public static boolean canPut(Object value) {
        if (value == null) {
            return false;
        }
        if (isBaseType(value)) {
            return true;
        }
        if (value instanceof JSONRecord) {
            return true;
        }
        if (value instanceof Recordset) {
            return true;
        }
        if (value instanceof Map) {
            return true;
        }
        if (value instanceof List) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isBaseType(Object oo) {
        if (oo == null) {
            return false;
        }
        return oo instanceof Integer || oo instanceof Long
                || oo instanceof Double || oo instanceof Float
                || oo instanceof String || oo instanceof Boolean
                || oo instanceof BigDecimal
                || oo instanceof Date;
    }

    public Optional getOptional(String key) {
        return Optional.ofNullable(this.get(key));
    }

    public <T> Optional<T> getOptional(String name, Class<T> type) {
        return Optional.ofNullable(this.getObject(name, type));
    }


    public JSONRecord() {
        super();
    }

    public JSONRecord(Map<String, Object> map) {
        super(map);
    }

    public static String new_string(byte b[], String charset) {
        try {
            return new String(b, charset);
        } catch (Exception e) {
            throw new RuntimeException("数据错误，不能将byte转为String " + charset, e);
        }
    }

    public JSONRecord(byte b[], String charset) {
        this(JSONRecord.getInstance(JSONRecord.new_string(b, charset)));
    }

    public JSONRecord(String str) {
        super(JSONRecord.getInstance(str));
        if (str.trim().startsWith("<")) {
            isXml = true;
        }
    }

    public static Map<String, Object> getInstance(String str) {
        if (str != null) {
            str = str.trim();
        }
        try {
            if (str.trim().startsWith("{")) {
                return JSON.parseObject(str, LinkedHashMap.class, Feature.OrderedField);
            } else if (str.trim().startsWith("<")) {
                return (XmlUtil.xml2mapWithAttr(str, false));
            } else {
                JSONObject ret = new JSONObject(true);
                ret.put("data", str);
                return ret;
            }
        } catch (RuntimeException e) {
            throw new RuntimeException("run_err", e);
        } catch (Exception e) {
            if (str.startsWith("<?") || str.startsWith("{\"") || str.startsWith("{'")) {
                throw new RuntimeException( "解析错误", e);
            }
            JSONObject ret = new JSONObject(true);
            ret.put("data", str);
            return ret;
        }
/*
		} catch( org.dom4j.DocumentException e ) {
			throw new TxnErrorException( "getStringMap", "getStringMap", e );
		}
*/
    }

    private static String readFileToString(File file, String charset) {
        return FileUtil.readUtf8String(file);
    }

    public JSONRecord(File file, String charset) {
        this(readFileToString(file, charset));
    }

    public JSONRecord(File file) {
        this(readFileToString(file, "UTF-8"));
    }

    public JSONRecord(boolean b) {
        super(b);
    }

    public JSONRecord(int i) {
        super(i);
    }

    public JSONRecord(int i, boolean b) {
        super(i, b);
    }

    public String toFormatJSONString() {
        return JSON.toJSONString(this, SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue,
                SerializerFeature.WriteDateUseDateFormat);
    }

    public String toXmlString() {
        return toXmlString("root");
    }

    public String toXmlString(boolean trimFlg) {
        return toXmlString("root", trimFlg);
    }

    public String toXmlString(String root_name, boolean trimFlg) {
        return toXmlString(root_name, null, trimFlg);
    }

    public String toXmlString(String root_name, String charset, boolean trimFlg) {
        try {
            return XmlUtil.toXmlString(super.getInnerMap(), root_name, charset, trimFlg);
        } catch (Exception e) {
            log.error("", e);
            return null;
        }
    }

    public String toXmlString(String root_name, String charset) {
        try {
            return toXmlString(root_name, charset, true);
        } catch (Exception e) {
            log.error("", e);
            return null;
        }
    }

    public String toXmlString(String root_name) {
        try {
            return toXmlString(root_name, null);
            //return XmlUtil.toXmlString(super.getInnerMap(), root_name);
        } catch (Exception e) {
            log.error("", e);
            return null;
        }
    }

    public JSONRecord moveValue2Attr() {
        JSONRecord ret = new JSONRecord(true);
        for (String key : this.keySet()) {
            Object oo = get(key);
            if (oo instanceof JSONRecord) {
                ret.put(key, ((JSONRecord) oo).moveValue2Attr());
                continue;
            } else if (oo instanceof Recordset) {
                Recordset array = new Recordset();
                for (Object one : (List) oo) {
                    JSONRecord j = ((JSONRecord) one).moveValue2Attr();
                    if (j.keySet().size() > 0) {
                        array.add(j);
                    }
                }
                if (array.size() > 0) {
                    ret.put(key, array);
                }
                continue;
            } else if (isBaseType(oo)) {
                if ("".equals(oo)) {
                    continue;
                }
            }
            if (key.startsWith("@")) {
                ret.put(key, oo);
            } else if (key.startsWith("#")) {
                ret.put("@" + key.substring(1), oo);
            } else {
                ret.put("@" + key, oo);
            }
        }
        return ret;
    }

    /*
        public JSONRecord removeAttr() {
        }
    */
    public JSONRecord moveAttr2Value() {
        try {
            JSONRecord ret = new JSONRecord(true);
            for (String key : this.keySet()) {
                try {
                    Object oo = get(key);
                    if (oo instanceof Map) {
                        ret.put(key, new JSONRecord(((Map) oo)).moveAttr2Value());
                        continue;
                    } else if (oo instanceof List) {
                        List array = new ArrayList<Map>();
                        for (Object one : (List) oo) {
                            JSONRecord j = new JSONRecord(((Map) one)).moveAttr2Value();
                            if (j.keySet().size() > 0) {
                                array.add(j);
                            }
                        }
                        if (array.size() > 0) {
                            ret.put(key, array);
                        }
                        continue;
                    } else if (isBaseType(oo)) {
                        if ("".equals(oo)) {
                            continue;
                        }
                        if (key.startsWith("@")) {
                            ret.put(key.substring(1), oo);
                        } else if (key.startsWith("#")) {
                            ret.put(key.substring(1), oo);
                        } else {
                            ret.put(key, oo);
                        }
                        continue;
                    }
                    log.error("无法解析的节点" + key);
                    log.error("无法解析的节点" + oo.getClass().getName());
                } catch (Exception e) {
                    throw new RuntimeException("moveAttr2Value ERROR", e);
                }
            }
            return ret;
        } catch (Exception e) {
            throw new RuntimeException("moveAttr2Value ERROR", e);
        }
    }

    @Override
    public JSONRecord getJSONObject(String str) {
        Object o = get(str);
        if (o == null) {
            return null;
        }
        return new JSONRecord((Map) o);
    }

    public Recordset getRecordset(String str) {
        Object oo = super.get(str);
        if (oo instanceof Map) {
            Recordset ret = new Recordset();
            ret.add(oo);
            return ret;
        }
        return new Recordset(super.getJSONArray(str));
    }

    public JSONRecord set(String str, Object obj) {
        return fluentPut(str, obj);
    }

    @Override
    public JSONRecord fluentPut(String str, Object obj) {
        super.fluentPut(str, obj);
        return this;
    }

    @Override
    public JSONRecord fluentPutAll(
            Map<? extends String, ? extends Object> a) {
        super.fluentPutAll(a);
        return this;
    }

    @Override
    public JSONRecord fluentClear() {
        super.fluentClear();
        return this;
    }

    @Override
    public JSONRecord fluentRemove(Object obj) {
        super.fluentRemove(obj);
        return this;
    }

    public JSONRecord getBaseNode(String key, int flag) {
        JSONRecord ret = this;
        if (key == null || key.trim().length() == 0) {
            return this;
        }
        String[] key_list = key.split(":");
        if (key_list.length == (1 - flag)) {
            return this;
        }
        Stack<String> stack = new Stack<String>();
        for (int ii = key_list.length - (2 - flag); ii >= 0; ii--) {
            String tmp_str = key_list[ii].trim();
            stack.push(tmp_str);
        }
        while (!stack.empty()) {
            String name = stack.pop().trim();
            if (name.length() == 0) {
                continue;
            }
            int idx = -1;
            if (name.matches("..*\\[\\s*\\d\\d*\\s*\\]")) {
                String idx_str = name.replaceAll(".*\\[\\s*", "").replaceAll("\\s*\\]", "");
                idx = Integer.valueOf(idx_str);
                name = name.replaceAll("\\s*\\[.*", "");
            }
            Object obj = ret.get(name);
            if (obj instanceof JSONRecord) {
                ret = (JSONRecord) obj;
            } else if (obj instanceof Map) {
                ret = new JSONRecord((Map) obj);
            } else if (obj instanceof List) {
                Recordset rs = new Recordset((List) obj);
                if (rs.size() == 0) {
                    rs.add(new JSONRecord(true));
                }
                ret = rs.getJSONRecord(idx > 0 ? idx : 0);
            } else {
                JSONRecord db = new JSONRecord(true);
                ret.put(name, db);
                ret = db;
            }
        }
        return ret;
    }

    public JSONRecord getBaseNode(String key) {

        return getBaseNode(key, 0);
/*
        if (key == null || key.trim().length() == 0)
            return null;
        if (key.contains(":") == false)
			return this;
		key = key.replaceAll(":[^:]*$", "").trim();
		return getOrCreateJSONObject( key );
*/
    }

    public JSONRecord getOrCreateRecord(String key) {

        if (true) {
            return getBaseNode(key, 1);
        }


        if (key == null || key.trim().length() == 0) {
            return null;
        }

        String first_key = key.replaceAll(":.*", "").trim();
        String sub_key = "";
        if (key.contains(":")) {
            sub_key = key.replaceAll("^[^:]*:", "").trim();
        }

        JSONRecord ret = null;

        Object oo = get(first_key);
        if (oo != null && oo instanceof List) {
            Recordset rs = new Recordset((List) oo);
            if (rs.size() > 0) {
                return rs.getJSONRecord(0);
            }
        }
        if (oo == null || oo instanceof Map == false) {
            ret = new JSONRecord(true);
            put(first_key, ret);
        }
        ret = new JSONRecord((Map) get(first_key));
        if (sub_key.trim().length() == 0) {
            return ret;
        }
        return ret.getOrCreateRecord(sub_key);

    }

    private Recordset replace2Array(JSONRecord base, String key, Object oo) {
        Recordset ret = new Recordset();
        if (oo == null) {
        } else if (oo instanceof JSONRecord) {
            ret.add(oo);
        } else if (oo instanceof Map) {
            ret.add(new JSONRecord((Map<String, Object>) oo));
        }
        base.put(key, ret);
        return ret;

    }

    public Recordset getOrCreateRecordset(String key) {
        JSONRecord base = getBaseNode(key);
        key = getTermName(key);

        Object oo = base.get(key);
        if (oo == null) {
            return replace2Array(base, key, oo);
        }
        if (oo instanceof Map) {
            return replace2Array(base, key, oo);
        }
        if (oo instanceof List) {
            return new Recordset((List<Object>) oo);
        } else {
            return replace2Array(base, key, oo);
        }
    }

    public void putTerm(String name, Object val) {
        if (name == null) {
            return;
        }
        JSONRecord node = getBaseNode(name);
        String term_name = getTermName(name);
        node.put(term_name, val);
    }

    private String getTermName(String name) {
        if (name == null) {
            return null;
        }
        String[] name_list = name.split(":");
        return name_list[name_list.length - 1].trim();

    }

    public <T> T superGetObject(String name, Class<T> type) {
        return super.getObject(name, type);
    }

    @Override
    public <T> T getObject(String name, Class<T> type) {
        if (name == null) {
            return null;
        }
        JSONRecord node = getBaseNode(name);
        String term_name = getTermName(name);
        Object obj = node.superGetObject(term_name, type);
        if (obj == null) {
            return null;
        }
        if (obj instanceof JSONObject && obj instanceof JSONRecord == false) {
            obj = new JSONRecord((Map) obj);
        } else if (obj instanceof JSONArray && obj instanceof Recordset == false) {
            obj = new Recordset((List) obj);
        }
        return (T) obj;
    }

    public Object getTermObject(String name) {
        if (name == null) {
            return null;
        }
        JSONRecord node = getBaseNode(name);
        String term_name = getTermName(name);
        return node.get(term_name);
    }

    public Object getObject(String name) {
        return getTermObject(name);
    }

    public void putValue(String name, Object o) {
        if (name == null) {
            return;
        }
        JSONRecord node = getBaseNode(name);
        String term_name = getTermName(name);
        node.put(term_name, o);
    }

    public String getFormatValue(String name, String format) {
        return getFormatValue(name, format, null);
    }

    public String getFormatValue(String name, String format, Object default_val) {
        Class cls_type = String.class;
        String type = format.replaceAll(".*%-?\\d*(\\.\\d*)?", "");
        if (type.startsWith("d")) {
            cls_type = Long.class;
        }
        if (type.startsWith("f")) {
            cls_type = Double.class;
        }

        Object obj = getObject(name, cls_type);
        if (obj == null) {
            obj = default_val;
        }
        return String.format(format, obj);

    }

    public String getValue(String name) {
        if (name == null) {
            return null;
        }
        JSONRecord node = getBaseNode(name);
        String term_name = getTermName(name);

        try {
            return node.getString(term_name);
            //return super.getString( name );
        } catch (Throwable e) {
            return null;
        }
    }

    public String getStringNoNull(String name) {
        String ret = getString(name);
        if (ret == null) {
            return "";
        }
        return ret;
    }

    public boolean isNull(String name) {
        if (name == null) {
            return true;
        }
        Object oo = get(name);
        if (oo == null) {
            return true;
        }
        if (oo instanceof String && ((String) oo).trim().length() == 0) {
            return true;
        }
        return false;
    }

    public void checkNull(String name) {
        if (getValue(name) == null) {
            throw new RuntimeException( "转换数值不能为null");
        }
    }

    public int getIntegerVal(String name, int def) {
        try {
            return getIntVal(name);
        } catch (Exception e) {
            return def;
        }

    }

    public int getIntVal(String name, int def) {
        try {
            return getIntVal(name);
        } catch (Exception e) {
            return def;
        }

    }

    public int getIntegerVal(String name) {
        JSONRecord node = getBaseNode(name);
        String term_name = getTermName(name);
        //return getIntVal( name );
        return node.getIntValue(term_name);

    }


    public int getIntVal(String name) {
        checkNull(name);
        JSONRecord node = getBaseNode(name);
        String term_name = getTermName(name);
        //return super.getIntValue( name );
        return node.getIntValue(term_name);

    }

    public int addIntVal(String name, int a) {
        int val = getIntVal(name) + a;
        put(name, val);
        return val;
    }


    public long getLongVal(String name) {
        checkNull(name);
        JSONRecord node = getBaseNode(name);
        String term_name = getTermName(name);
        //return super.getLongValue( name );
        return node.getLongValue(term_name);
    }

    public long getLongVal(String name, int def) {
        try {
            return getLongVal(name);
        } catch (Exception e) {
            return def;
        }
    }


    public double getAbsDoubleVal(String name, double def) {
        double ret = getDoubleVal(name, def);
        if (ret < 0) {
            return ret * (-1);
        } else {
            return ret;
        }
    }

    public BigDecimal getBigDecimal(String name, int dec) {

        BigDecimal ret = this.getBigDecimal(name);
        if (ret != null) {
            ret = ret.setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        return ret;
    }

    public double getDoubleVal(String name, double def) {
        try {
            return getDoubleVal(name);
        } catch (Exception e) {
            return def;
        }

    }

    public double getDoubleVal(String name) {
        checkNull(name);
        JSONRecord node = getBaseNode(name);
        String term_name = getTermName(name);
        //return super.getDoubleValue( name );
        return node.getDoubleValue(term_name);
    }

    public String getStringVal(String name) {
        checkNull(name);
        return super.getString(name);
    }

    public Boolean getBoolean(String name, boolean b) {
        try {
            checkNull(name);
            JSONRecord node = getBaseNode(name);
            String term_name = getTermName(name);
            Boolean ret = node.getBoolean(term_name);
            if (ret == null) {
                return b;
            }
            return ret;
        } catch (Exception e) {
            return b;
        }
    }


    public Recordset getRecordsetOrNull(String str) {
        if (nodeIsNull(str)) {
            return null;
        }
        return getRecordset(str);

    }

    public boolean nodeIsNull(String nodeName) {
        Object obj = get(nodeName);
        if (obj == null) {
            return true;
        }
        if (obj instanceof String && ((String) obj).trim().length() == 0) {
            return true;
        }
        if (obj instanceof List && ((List) obj).size() == 0) {
            return true;
        }
        if (obj instanceof Map && ((Map) obj).isEmpty()) {
            return true;
        }
        return false;
    }

    public JSONRecord getRecordOrNull(String nodeName) {
        if (nodeIsNull(nodeName)) {
            return null;
        }
        return new JSONRecord(super.getJSONObject(nodeName));
    }

    public JSONRecord getRecord(String nodeName) {
        return new JSONRecord(super.getJSONObject(nodeName));
    }

    public void replaceAll(String name, String from, String to) {
        JSONRecord node = getBaseNode(name);
        String term_name = name.replaceAll(".*:", "");

        String value = node.getValue(term_name);
        if (value == null) {
            return;
        }
        value = value.replaceAll(from, to);
        node.put(term_name, value);
    }

    /**
     * 转换成XML报文格式
     * <p>把数据总线转换成XML报文，根路径为data-bus，不包含head
     *
     * @return java.lang.String
     */

    @Override
    public String toString() {
        return toJSONString();
    }

    public String toString(boolean b) {
        return toJSONString(this, b);
    }

    public String toString(String rootNode) {
        return toXmlString(rootNode);
    }


    public String toJsonString() {
        return toJSONString(this);
    }

    public Object getProperty(String name) {
        return get(name);
    }

    /**
     * 设置属性
     *
     * @param name
     * @param value
     */
    public void setProperty(String name, Object value) {
        put(name, value);
    }

    /*
        public JSONRecord clone() {
            return this;
        }
    */
    public void putIfNoValue(String key, Object val) {
        if (!nodeIsNull(key)) {
            return;
        }
        super.put(key, val);
    }

    //尚未完成编写
    public Recordset toKVList() {
        Recordset ret = new Recordset();
        for (String key : keySet()) {
            Object val = get(key);
            if (isBaseType(val)) {
            }
        }
        return ret;
    }

    public JSONRecord join(JSONRecord in) {
        return join(in, false);
    }

    public JSONRecord join(JSONRecord in, boolean join_all) {
        if (in == null) {
            return this;
        }
        for (String key : in.keySet()) {
            Object obj = in.get(key);
            if ((isBaseType(obj) == false && join_all == false)) {
                continue;
            }
            if (get(key) == null) {
                put(key, obj);
            }
        }
        return this;
    }

    public static String getErrorMsg(Throwable e) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        e.printStackTrace(new java.io.PrintStream(baos));
        return baos.toString();

    }

    public JSONRecord toJSONRecord() {
        JSONRecord ret = new JSONRecord();

        Iterator<String> iterator = this.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            Object oo = this.get(key);
            if (oo == null) {
            } else if (oo instanceof Map) {
                JSONRecord record = new JSONRecord((Map) oo);
                ret.put(key, record.toJSONRecord());
            } else if (oo instanceof List) {
                Recordset rs = new Recordset();
                for (JSONRecord record : new Recordset((List) oo).list()) {
                    rs.add(record.toJSONRecord());
                }
                ret.put(key, rs);
            } else {
                ret.put(key, oo);
            }
        }
        return ret;
    }

    public JSONRecord getOrParseRecord(String key) {
        Object obj = get(key);
        if (obj == null) {
            return getOrCreateRecord(key);
        }
        if (obj instanceof JSONRecord) {
            return (JSONRecord) obj;
        }
        if (obj instanceof Map) {
            return new JSONRecord((Map) obj);
        }
        if (obj instanceof String) {
            JSONRecord ret = new JSONRecord((String) obj);
            this.put(key, ret);
            return ret;
        }
        return null;
    }

    public JSONRecord convName() {
        JSONRecord ret = new JSONRecord(true);
        for (String key : this.keySet()) {
            Object oo = this.get(key);
            if (oo == null) {
            } else if (oo instanceof Map) {
                JSONRecord record = new JSONRecord((Map) oo);
                record.convName();
                ret.put(conv(key), record);
            } else if (oo instanceof List) {
                Recordset rs = new Recordset();
                for (JSONRecord record : new Recordset((List) oo).list()) {
                    JSONRecord j = record.convName();
                    rs.add(j);
                }
                ret.put(conv(key), rs);
            } else {
                ret.put(conv(key), oo);
            }
        }
        return ret;
    }

    protected static String conv(String instr) {
        if (instr.startsWith("__")) {
            return instr;
        }
        String outstr = null;
        try {
            //instr = "_" + instr;
            byte[] indata = instr.getBytes();
            int diff = 'A' - 'a';
            for (int ii = 0; ii < indata.length; ii++) {
                if (indata[ii] == '_' && (indata[ii + 1] >= 'a' && indata[ii + 1] <= 'z')) {
                    indata[ii + 1] += diff;
                }
            }

            outstr = new String(indata);
            outstr = outstr.replaceAll("_", "");
            return outstr.replaceAll("Uuid", "UUID");
        } catch (Exception e) {
            return instr;
        }

    }

    public JSONRecord onlyValues() {
        String[] val = null;
        return onlyValues(val);
    }

    public JSONRecord onlyValues(String... uninclude_key) {
        this.entrySet().removeIf(entry ->
                !isBaseType(entry.getValue()) &&
                        (uninclude_key == null ||
                                !Arrays.stream(uninclude_key).anyMatch(str -> str.equals(entry.getKey()))
                        )
        );
        return this;
    }

}


/*
 */
