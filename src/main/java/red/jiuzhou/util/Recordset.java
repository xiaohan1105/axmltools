package red.jiuzhou.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
public class Recordset extends com.alibaba.fastjson.JSONArray {
    //protected static final  Logger log = LogManager.getLogger( Recordset.class.getName() );

    public Recordset() {
        super();

    }

    public Recordset(String str) {
        super(parseArray(str));
    }

    public Recordset(List array) {
        super((List<Object>) array);
    }

    public Recordset(int a) {
        super(a);
    }

    @Override
    public Recordset fluentAdd(Object a) {
        return new Recordset(super.fluentAdd(a));
    }

    @Override
    public Recordset fluentRemove(Object a) {
        return new Recordset(super.fluentRemove(a));
    }

    @Override
    public Recordset fluentAddAll(java.util.Collection<? extends Object> a) {
        return new Recordset(super.fluentAddAll(a));
    }

    @Override
    public Recordset fluentAddAll(int a, java.util.Collection<? extends Object> b) {
        return new Recordset(super.fluentAddAll(a, b));
    }

    @Override
    public Recordset fluentRemoveAll(java.util.Collection<?> a) {
        return new Recordset(super.fluentRemoveAll(a));
    }

    @Override
    public Recordset fluentRetainAll(java.util.Collection<?> a) {
        return new Recordset(super.fluentRetainAll(a));
    }

    @Override
    public Recordset fluentClear() {
        return new Recordset(super.fluentClear());
    }

    @Override
    public Recordset fluentSet(int a, Object b) {
        return new Recordset(super.fluentSet(a, b));
    }

    @Override
    public Recordset fluentAdd(int a, Object b) {
        return new Recordset(super.fluentAdd(a, b));
    }

    @Override
    public Recordset fluentRemove(int a) {
        return new Recordset(super.fluentRemove(a));
    }

    @Override
    public JSONRecord getJSONObject(int a) {
        return new JSONRecord(super.getJSONObject(a));
    }

    @Override
    public Recordset getJSONArray(int a) {
        return new Recordset(super.getJSONArray(a));
    }

    /**
     * 根据关键字获取记录
     *
     * @param keyName  节点名称
     * @param keyValue 节点内容
     * @return
     */
    public JSONRecord getByKey(String keyName, String keyValue) {
        if (keyValue == null) {
            return null;
        }

        // 定位
        String value;
        for (int ii = 0; ii < size(); ii++) {
            value = getJSONObject(ii).getString(keyName);
            if (value != null && value.compareTo(keyValue) == 0) {
                return new JSONRecord(getJSONObject(ii));
            }
        }
        return null;
    }

    public JSONRecord get(String keyName, String keyValue) {
        return get(keyName, keyValue, true);
    }

    public JSONRecord get(String keyName, String keyValue, boolean b) {
        if (keyValue == null) {
            throw new RuntimeException("查询记录的关键字内容为空");
        }

        // 定位
        String value;
        for (int ii = 0; ii < size(); ii++) {
            value = getJSONObject(ii).getString(keyName);
            if (value != null && value.compareTo(keyValue) == 0) {
                return new JSONRecord(getJSONObject(ii));
            }
        }

        if (b == false) {
            return null;
        }

        // 如果没有找到记录，返回错误
        throw new RuntimeException(
                "没有找到记录[" + keyName + "][" + keyValue + "]的内容" + this.toString()
        );
    }

    /**
     * 取满足条件的所有记录
     *
     * @param keyName  节点名称
     * @param keyValue 节点内容
     * @return
     */
    public Recordset filter(String keyName, String... keyValue) {
        if (keyValue.length == 0) {
            throw new RuntimeException(
                    "查询记录的关键字内容为空"
            );
        }

        // 结果
        Recordset result = new Recordset();

        for (int ii = 0; ii < size(); ii++) {
            String value = getJSONObject(ii).getValue(keyName);
            if (value != null) {
                for (String v : keyValue) {
                    if (value.compareTo(v) == 0) {
                        result.add(getJSONObject(ii));
                        break;
                    }
                }
            }
        }

        return result;
    }

    public String toFormatJSONString() {
        return JSON.toJSONString(this,
                SerializerFeature.PrettyFormat,
                SerializerFeature.WriteMapNullValue,
                SerializerFeature.MapSortField,
                SerializerFeature.WriteDateUseDateFormat);
    }

    public List<JSONRecord> array() {
        //return toJavaList( JSONRecord.class );
        List<JSONRecord> ret = new ArrayList<JSONRecord>();
        for (Object one : this) {
            ret.add(new JSONRecord((Map) one));
        }
        return ret;
    }

    public String toXmlString(boolean b) {
        StringBuffer sb = new StringBuffer();
        for (JSONRecord one : array()) {
            sb.append(one.toXmlString());
        }
        return sb.toString();
    }

    public void sort(String names) {
        String[] name_list = names.split(",");
        Boolean[] order_list = new Boolean[name_list.length];

        for (int ii = 0; ii < name_list.length; ii++) {
            order_list[ii] = false;
            if (name_list[ii].endsWith("|desc")) {
                order_list[ii] = true;
            }
            name_list[ii] = name_list[ii].replaceAll("\\|desc$", "");
        }


        String name = name_list[0];
        String next_name = null;
        List<Comparator> cmp_list = new ArrayList<>();

        Comparator comparator = Comparator.comparing(e -> (new JSONRecord((Map) e)).getValue(name));
        if (order_list[0]) {
            comparator = comparator.reversed();
        }
        Comparator next = null;
/*
		for( int ii = 1; ii < name_list.length; ii ++ ) {
			next_name =  new String( name_list[ii] );
			next = Comparator.comparing( e -> ( new JSONRecord( ( Map )e ) ).getValue( next_name ));
			cmp_list.add( next );
		}
*/
        if (name_list.length >= 2) {
            next = Comparator.comparing(e -> (new JSONRecord((Map) e)).getValue(name_list[1]));
            if (order_list[1]) {
                next = next.reversed();
            }
            cmp_list.add(next);
        }
        if (name_list.length >= 3) {
            next = Comparator.comparing(e -> (new JSONRecord((Map) e)).getValue(name_list[2]));
            if (order_list[2]) {
                next = next.reversed();
            }
            cmp_list.add(next);
        }
        if (name_list.length >= 4) {
            next = Comparator.comparing(e -> (new JSONRecord((Map) e)).getValue(name_list[3]));
            if (order_list[3]) {
                next = next.reversed();
            }
            cmp_list.add(next);
        }
        for (Comparator c : cmp_list) {
            comparator = comparator.thenComparing(c);
        }
        sort(
                Comparator.nullsFirst(
                        comparator
                )
        );
/*
		for( ii = 1; ii < name_list.length; ii ++ ) {
			Comparator next = Comparator.comparing( e -> ( new JSONRecord( ( Map )e ) ).getValue( name_list[ii] ));
			comparator = comparator.thenComparing( next);
		}
*/
		
/*
		if( name_list.length == 1 )
			sort(
				Comparator.nullsFirst(
					comparator
				)
			);
		if( name_list.length == 2 )
			sort(
				Comparator.nullsFirst(
					//Comparator.comparing(e -> ( new JSONRecord( ( Map )e ) ).getValue( name ))
					comparator.thenComparing( Comparator.comparing(e -> ( new JSONRecord( ( Map )e ) ).getValue( name_list[1] )) );
				)
			);
*/
    }

    /*
        public void sort( String names ) {
            String[] name_list = names.split( "," );
            String name = name_list[0];
            Comparator comparator = Comparator.comparing( e -> ( new JSONRecord( ( Map )e ) ).getValue( name ));
            for( int ii = 1; ii < name_list.length; ii ++ ) {
                comparator = comparator.thenComparing( e -> ( new JSONRecord( ( Map )e ) ).getValue( name_list[ii] ));
            }

            sort(
                Comparator.nullsFirst(
                    //Comparator.comparing(e -> ( new JSONRecord( ( Map )e ) ).getValue( name ))
                    comparator
                )
            );
        }
        public void sort( String names ) {
            sort(
                Comparator.nullsFirst(
                    Comparator.comparing(e -> ( new JSONRecord( ( Map )e ) ).getValue( name ))
                )
            );
        }
    */
    public void sortInt(String name) {
        sort(
                Comparator.nullsFirst(
                        Comparator.comparingInt(e -> (new JSONRecord((Map) e)).getIntVal(name, 0))
                )
        );
    }

    public void sortDouble(String name) {
        sort(
                Comparator.nullsFirst(
                        Comparator.comparingDouble(e -> (new JSONRecord((Map) e)).getDoubleVal(name, 0))
                )
        );
    }

    public String toString(boolean b) {
        return toJSONString(this, b);
    }

    public JSONRecord addNewItem() {
        JSONRecord data = new JSONRecord();
        add(data);
        return data;
    }

    public JSONRecord getJSONRecord(int index) {
        return getJSONObject(index);
    }

    public List<JSONRecord> list() {
        //return toJavaList( JSONRecord.class );
        List<JSONRecord> ret = new ArrayList<JSONRecord>();
        for (Object one : this) {
            ret.add(new JSONRecord((Map) one));
        }
        return ret;
    }

    public JSONRecord getJSONRecord1(int i) {
        Object ret = get(i);
        return (JSONRecord) ret;
    }

    public void addAll(Recordset rs) {
        for (JSONRecord one : rs.list()) {
            add(one);
        }
    }

    public static Recordset toRecordset(Object obj) {
        if (obj instanceof Recordset) {
            return (Recordset) obj;
        }
        if (obj instanceof List) {
            return new Recordset((List) obj);
        }
        if (obj instanceof JSONRecord) {
            Recordset ret = new Recordset();
            ret.add((JSONRecord) obj);
            return ret;
        }
        if (obj instanceof Map) {
            Recordset ret = new Recordset();
            ret.add(new JSONRecord((Map) obj));
            return ret;
        } else {
            return null;
        }
    }

    public Recordset group(String names, String sum_names) {
        sort(names);
        Recordset ret = uniq(names, true);
        return ret;
    }

    public Recordset uniq(String names) {
        return uniq(names, false);
    }

    public Recordset uniq(String names, boolean flag) {
        sort(names);
        String[] name_list = names.split(",");
        for (int ii = 0; ii < name_list.length; ii++) {
            name_list[ii] = name_list[ii].replaceAll("\\|desc$", "");
        }

        Recordset ret = new Recordset();
        String last_value = null;
        JSONRecord last_item = null;
        for (JSONRecord item : list()) {
            StringBuffer value_sb = new StringBuffer();
            for (String name : name_list) {
                Object obj = item.getObject(name);
                if (obj == null) {
                    obj = "null";
                }
                value_sb.append(obj.toString()).append("|");
            }
/*
			if( JSONRecord.isBaseType( obj ) == false )
				continue;
*/
            String value = value_sb.toString();
            if (value.equals(last_value)) {
                if (flag && last_item != null) {
                    last_item.getOrCreateRecordset("__data_list").add(item);
                }
                continue;
            }
            if (flag) {
                JSONRecord one = new JSONRecord(true);
                last_item = one;
                for (String name : name_list) {
                    one.put(name, item.getObject(name));
                }
                one.getOrCreateRecordset("__data_list").add(item);
                ret.add(one);
            } else {
                ret.add(item);
            }

            last_value = value_sb.toString();
        }
        return ret;
    }

    public Recordset select(String... keys) {
        Recordset newArray = this.stream()
                .map(obj -> {
                    Map jsonObject = (Map) obj;
                    JSONRecord newObj = new JSONRecord();
                    for (String key : keys) {
                        newObj.put(key, ((Map) obj).get(key));
                    }
                    return newObj;
                })
                .collect(Recordset::new, Recordset::add, Recordset::addAll);

        return newArray;
    }

}
