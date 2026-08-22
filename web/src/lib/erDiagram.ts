import type { SchemaForeignKey, SchemaTable } from "../api/admin";

const UNSAFE_CHARS = /[^A-Za-z0-9_]+/g;

function safeType(type: string): string {
  const cleaned = type.replace(UNSAFE_CHARS, "_").replace(/^_+|_+$/g, "");
  return cleaned || "unknown";
}

function primaryKeyColumns(table: SchemaTable): string[] {
  return table.columns.filter((column) => column.primary_key).map((column) => column.name);
}

export function relationshipFor(table: SchemaTable, fk: SchemaForeignKey): string {
  const pk = primaryKeyColumns(table);

  if (pk.includes(fk.column)) {
    return pk.length === 1 ? "||--||" : "||--o{";
  }
  return "||..o{";
}

export function buildErDiagram(tables: SchemaTable[]): string {
  const lines: string[] = ["erDiagram"];

  for (const table of tables) {
    if (table.columns.length === 0) {
      lines.push(`  ${table.name} {`, `    unknown placeholder`, `  }`);
      continue;
    }

    const foreignKeyColumns = new Set(table.foreign_keys.map((fk) => fk.column));
    lines.push(`  ${table.name} {`);

    for (const column of table.columns) {
      const markers = [
        column.primary_key ? "PK" : null,
        foreignKeyColumns.has(column.name) ? "FK" : null,
      ].filter(Boolean);
      const suffix = markers.length > 0 ? ` ${markers.join(",")}` : "";
      lines.push(`    ${safeType(column.type)} ${column.name}${suffix}`);
    }

    lines.push("  }");
  }

  for (const table of tables) {
    for (const fk of table.foreign_keys) {
      const relation = relationshipFor(table, fk);
      lines.push(`  ${fk.references_table} ${relation} ${table.name} : "${fk.column}"`);
    }
  }

  return lines.join("\n");
}
