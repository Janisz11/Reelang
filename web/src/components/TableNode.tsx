import { memo } from "react";
import { Handle, Position, type NodeProps } from "@xyflow/react";
import {
  NODE_WIDTH,
  sourceHandleId,
  targetHandleId,
  type TableNode as TableNodeType,
} from "../lib/schemaGraph";

function shortType(type: string): string {
  return type.replace(/\s+/g, " ").replace(/VARCHAR\((\d+)\)/i, "varchar($1)").toLowerCase();
}

export const TableNode = memo(({ data, selected }: NodeProps<TableNodeType>) => (
  <div
    className={`table-node${selected ? " table-node--selected" : ""}`}
    style={{ width: NODE_WIDTH }}
  >
    <div className="table-node__header">{data.table}</div>
    <div className="table-node__body">
      {data.columns.map((column) => (
        <div key={column.name} className="table-node__row">
          {column.hasTargetHandle && (
            <Handle
              type="target"
              position={Position.Left}
              id={targetHandleId(column.name)}
              className="table-node__handle"
            />
          )}
          {column.hasSourceHandle && (
            <Handle
              type="source"
              position={Position.Right}
              id={sourceHandleId(column.name)}
              className="table-node__handle"
            />
          )}

          <span
            className={`table-node__name${column.primaryKey ? " table-node__name--pk" : ""}`}
            title={column.name}
          >
            {column.name}
          </span>

          <span className="table-node__badges">
            {column.primaryKey && <span className="table-node__badge table-node__badge--pk">PK</span>}
            {column.foreignKey && <span className="table-node__badge table-node__badge--fk">FK</span>}
            {column.nullable && <span className="table-node__badge table-node__badge--null">NULL</span>}
          </span>

          <span className="table-node__type" title={column.type}>
            {shortType(column.type)}
          </span>
        </div>
      ))}
      {data.columns.length === 0 && <div className="table-node__row table-node__row--empty">brak kolumn</div>}
    </div>
  </div>
));

TableNode.displayName = "TableNode";
