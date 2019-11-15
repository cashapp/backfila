package app.cash.backfila.client.misk.base;


import misk.hibernate.Id;

public class PkeySqlAdapter {
    public Object fromString(Class<?> type, String sqlString) {
        if (type == String.class) return sqlString;
        if (type == Id.class) return new Id(Long.parseLong(sqlString));
        throw new IllegalArgumentException(
                String.format("Unsupported backfill primary key type: %s. Add an adapter to this class or "
                        + "use a different type as your pkey.", type));
    }
}
