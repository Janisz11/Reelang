import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Background,
  BackgroundVariant,
  Controls,
  MiniMap,
  ReactFlow,
  useEdgesState,
  useNodesInitialized,
  useNodesState,
  useReactFlow,
  ReactFlowProvider,
  type Edge,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { fetchSchema, type SchemaResponse } from "../api/admin";
import { describeAdminError, useAdminToken } from "../lib/useAdminToken";
import { AdminTokenGate } from "../components/AdminTokenGate";
import {
  buildSchemaGraph,
  layoutGraph,
  type LayoutDirection,
  type TableNode as TableNodeType,
} from "../lib/schemaGraph";
import { TableNode } from "../components/TableNode";
import { ErrorBox, LoadingBox } from "../components/common";

const DIRECTION: LayoutDirection = "LR";

const nodeTypes = { table: TableNode };

function SchemaFlow({
  schema,
  onRefresh,
  loading,
  onChangeToken,
}: {
  schema: SchemaResponse;
  onRefresh: () => void;
  loading: boolean;
  onChangeToken: () => void;
}) {
  const [nodes, setNodes, onNodesChange] = useNodesState<TableNodeType>([]);
  const [edges, setEdges] = useEdgesState<Edge>([]);
  const { fitView } = useReactFlow();
  const nodesInitialized = useNodesInitialized();
  const shouldFit = useRef(false);

  const graph = useMemo(() => buildSchemaGraph(schema.tables), [schema]);

  const applyAutoLayout = useCallback(() => {
    setNodes(layoutGraph(graph.nodes, graph.edges, DIRECTION));
    setEdges(graph.edges);
    shouldFit.current = true;
  }, [graph, setNodes, setEdges]);

  useEffect(() => {
    applyAutoLayout();
  }, [applyAutoLayout]);

  useEffect(() => {
    if (!shouldFit.current || !nodesInitialized) return;
    shouldFit.current = false;
    void fitView({ padding: 0.12, duration: 300 });
  }, [nodes, nodesInitialized, fitView]);

  const edgeCount = graph.edges.length;

  return (
    <>
      <div className="admin-toolbar">
        <span className="muted">
          {schema.tables.length} tabel · {edgeCount} relacji — struktura odczytana na żywo z bazy.
        </span>
        <span className="admin-toolbar__spacer" />
        <button className="btn btn--outline btn--sm" onClick={applyAutoLayout}>
          Wyrównaj automatycznie
        </button>
        <button className="btn btn--outline btn--sm" onClick={onRefresh} disabled={loading}>
          Odśwież
        </button>
        <button className="btn btn--neutral btn--sm" onClick={onChangeToken}>
          Zmień token
        </button>
      </div>

      <div className="schema-flow">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          nodeTypes={nodeTypes}
          nodesDraggable
          nodesConnectable={false}
          elementsSelectable
          deleteKeyCode={null}
          minZoom={0.1}
          maxZoom={2}
        >
          <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="#d8ccc3" />
          <MiniMap pannable zoomable nodeColor="#b22222" nodeStrokeWidth={2} />
          <Controls showInteractive={false} />
        </ReactFlow>
      </div>
    </>
  );
}

export function SchemaViewer() {
  const { token, submitToken, clearToken } = useAdminToken();
  const [schema, setSchema] = useState<SchemaResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (activeToken: string) => {
    setLoading(true);
    setError(null);
    try {
      setSchema(await fetchSchema(activeToken));
    } catch (err) {
      setSchema(null);
      setError(describeAdminError(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (token) void load(token);
  }, [token, load]);

  const changeToken = () => {
    clearToken();
    setSchema(null);
    setError(null);
  };

  if (!token) return <AdminTokenGate submitLabel="Pokaż schemat" onSubmit={submitToken} />;

  if (loading && !schema) return <LoadingBox />;

  if (error) {
    return (
      <div className="center-box">
        <ErrorBox message={error} onRetry={() => void load(token)} />
        <button className="btn btn--neutral btn--sm" onClick={changeToken}>
          Zmień token
        </button>
      </div>
    );
  }

  if (!schema) return <LoadingBox />;

  return (
    <ReactFlowProvider>
      <SchemaFlow
        schema={schema}
        loading={loading}
        onRefresh={() => void load(token)}
        onChangeToken={changeToken}
      />
    </ReactFlowProvider>
  );
}
