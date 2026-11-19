package com.example.classmanagement.business;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.*;

/** SQL 仅来自业务服务的固定语句；客户端不能传入 SQL、表名或字段名。 */
@Repository
public class BusinessRepository {
    private final JdbcTemplate jdbc;
    public BusinessRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Map<String,Object>> query(String sql,Object... args) {
        return jdbc.query(sql,(rs,n)-> {
            Map<String,Object> row=new LinkedHashMap<>();
            var meta=rs.getMetaData();
            for(int i=1;i<=meta.getColumnCount();i++) {
                String key=meta.getColumnLabel(i).toLowerCase(Locale.ROOT);
                StringBuilder camel=new StringBuilder(); boolean upper=false;
                for(char c:key.toCharArray()) { if(c=='_') upper=true; else {camel.append(upper?Character.toUpperCase(c):c);upper=false;} }
                Object value=rs.getObject(i);
                if(value instanceof java.sql.Date d) value=d.toLocalDate().toString();
                if(value instanceof Timestamp t) value=t.toLocalDateTime().toString();
                if(value instanceof Time t) value=t.toLocalTime().toString();
                if(value instanceof java.time.temporal.TemporalAccessor) value=value.toString();
                row.put(camel.toString(),value);
            }
            return row;
        },args);
    }
    public Map<String,Object> one(String sql,Object... args) {
        return required("记录",sql,args);
    }
    public Map<String,Object> required(String label,String sql,Object... args) {
        var rows=query(sql,args); if(rows.isEmpty()) throw new NoSuchElementException(label+"不存在");return rows.get(0);
    }
    public long insert(String sql,Object... args) {
        var key=new GeneratedKeyHolder();
        jdbc.update(c->{var s=c.prepareStatement(sql,new String[]{"id"}); for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    public int update(String sql,Object... args) { return jdbc.update(sql,args); }
}
