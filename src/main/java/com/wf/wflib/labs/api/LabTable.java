package com.wf.wflib.labs.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Column schema of one lab table. Units SI; {@code mm_rha} penetration, {@code deg} angles, {@code ""} none.
 * Row values are {@link Number} for {@link Kind#NUM} (Boolean accepted as 1/0), {@link String} for {@link Kind#STR};
 * null => JSON null.
 */
public record LabTable(String name, List<Column> columns) {

    public enum Role {
        DIM,
        MEASURE
    }

    public enum Kind {
        NUM,
        STR
    }

    public record Column(String name, String unit, Role role, Kind kind) {
    }

    public LabTable {
        columns = List.copyOf(columns);
    }

    public static Builder of(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private final List<Column> columns = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder dimStr(String column) {
            return add(column, "", Role.DIM, Kind.STR);
        }

        public Builder dimNum(String column, String unit) {
            return add(column, unit, Role.DIM, Kind.NUM);
        }

        public Builder measure(String column, String unit) {
            return add(column, unit, Role.MEASURE, Kind.NUM);
        }

        public Builder measureStr(String column) {
            return add(column, "", Role.MEASURE, Kind.STR);
        }

        public Builder add(String column, String unit, Role role, Kind kind) {
            columns.add(new Column(column, unit, role, kind));
            return this;
        }

        public LabTable build() {
            return new LabTable(name, columns);
        }
    }
}
