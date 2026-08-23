import dagre from "dagre";
import { MarkerType, Position, type Edge, type Node } from "@xyflow/react";
import type { SchemaTable } from "../api/admin";

export const NODE_WIDTH = 264;
const HEADER_HEIGHT = 38;
const ROW_HEIGHT = 24;
const BODY_PADDING = 8;

export type LayoutDirection = "TB" | "LR";

export interface TableColumnView {
  name: string;
  type: string;
  nullable: boolean;
  primaryKey: boolean;
  foreignKey: boolean;
  hasSourceHandle: boolean;
  hasTargetHandle: boolean;
}

export type TableNodeData = {
  table: string;
  columns: TableColumnView[];
};

export type TableNode = Node<TableNodeData, "table">;

export function sourceHandleId(column: string): string {
  return `${column}--source`;
}

export function targetHandleId(column: string): string {
  return `${column}--target`;
}

export function nodeHeight(columnCount: number): number {
  return HEADER_HEIGHT + BODY_PADDING * 2 + Math.max(columnCount, 1) * ROW_HEIGHT;
}

export interface SchemaGraph {
  nodes: TableNode[];
  edges: Edge[];
}

export function buildSchemaGraph(tables: SchemaTable[]): SchemaGraph {
  const known = new Set(tables.map((table) => table.name));
  const referenced = new Map<string, Set<string>>();

  for (const table of tables) {
    for (const fk of table.foreign_keys) {
      if (!known.has(fk.references_table)) continue;
      const columns = referenced.get(fk.references_table) ?? new Set<string>();
      columns.add(fk.references_column);
      referenced.set(fk.references_table, columns);
    }
  }

  const nodes: TableNode[] = tables.map((table) => {
    const fkColumns = new Set(table.foreign_keys.map((fk) => fk.column));
    const incoming = referenced.get(table.name) ?? new Set<string>();

    return {
      id: table.name,
      type: "table",
      position: { x: 0, y: 0 },
      data: {
        table: table.name,
        columns: table.columns.map((column) => ({
          name: column.name,
          type: column.type,
          nullable: column.nullable,
          primaryKey: column.primary_key,
          foreignKey: fkColumns.has(column.name),
          hasSourceHandle: incoming.has(column.name),
          hasTargetHandle: fkColumns.has(column.name),
        })),
      },
    };
  });

  const edges: Edge[] = [];

  for (const table of tables) {
    for (const fk of table.foreign_keys) {
      if (!known.has(fk.references_table)) continue;
      edges.push({
        id: `${table.name}.${fk.column}->${fk.references_table}.${fk.references_column}`,
        source: fk.references_table,
        sourceHandle: sourceHandleId(fk.references_column),
        target: table.name,
        targetHandle: targetHandleId(fk.column),
        type: "smoothstep",
        markerEnd: { type: MarkerType.ArrowClosed, width: 16, height: 16, color: "#b22222" },
        style: { stroke: "#b22222", strokeWidth: 1.6 },
      });
    }
  }

  return { nodes, edges };
}

export function layoutGraph(
  nodes: TableNode[],
  edges: Edge[],
  direction: LayoutDirection,
): TableNode[] {
  const graph = new dagre.graphlib.Graph();
  graph.setDefaultEdgeLabel(() => ({}));
  graph.setGraph({
    rankdir: direction,
    nodesep: direction === "LR" ? 46 : 64,
    ranksep: direction === "LR" ? 150 : 96,
    marginx: 48,
    marginy: 48,
  });

  for (const node of nodes) {
    graph.setNode(node.id, { width: NODE_WIDTH, height: nodeHeight(node.data.columns.length) });
  }

  for (const edge of edges) {
    graph.setEdge(edge.source, edge.target);
  }

  dagre.layout(graph);

  const horizontal = direction === "LR";

  return nodes.map((node) => {
    const positioned = graph.node(node.id);
    const height = nodeHeight(node.data.columns.length);

    return {
      ...node,
      sourcePosition: horizontal ? Position.Right : Position.Bottom,
      targetPosition: horizontal ? Position.Left : Position.Top,
      position: {
        x: positioned.x - NODE_WIDTH / 2,
        y: positioned.y - height / 2,
      },
    };
  });
}
