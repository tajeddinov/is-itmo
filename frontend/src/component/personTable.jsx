'use client';

import React, {useCallback, useMemo, useRef} from "react";
import {AgGridReact} from "ag-grid-react";
import {AllCommunityModule, colorSchemeDark, iconSetMaterial, ModuleRegistry, themeQuartz} from "ag-grid-community";
import {API_BASE} from "../../cfg.js";

ModuleRegistry.registerModules([AllCommunityModule]);

export const personTableTheme = themeQuartz
    .withPart(iconSetMaterial)
    .withPart(colorSchemeDark)
    .withParams({
        backgroundColor: "#0f172a",
        foregroundColor: "#e5e7eb",
        headerBackgroundColor: "#111827",
        headerTextColor: "#e5e7eb",
        oddRowBackgroundColor: "#0b1326",
        accentColor: "#60a5fa",
        headerColumnResizeHandleColor: "#60a5fa",
        borderColor: "#1e293b",
        rowHoverColor: "#1f2937",
        selectedRowBackgroundColor: "#1e3a8a",
    });

export default function PersonTable({onOpenEditPersonModal, onReadyRefresh, onReadyControls}) {
    const gridApiRef = useRef(null);

    const colDefs = useMemo(() => ([
        {
            headerName: "ID",
            field: "id",
            colId: "id",
            width: 100,
            sortable: true,
            filter: "agNumberColumnFilter",
            floatingFilter: true
        },
        {
            headerName: "Edit",
            filter: false,
            floatingFilter: false,
            sortable: false,
            width: 90,
            cellRenderer: (p) => (
                <button
                    style={{
                        paddingInline: 15,
                        border: "none",
                        background: "#007bff",
                        color: "#fff",
                        borderRadius: 5,
                        cursor: "pointer"
                    }}
                    onClick={() => onOpenEditPersonModal?.(p.data)}
                >
                    Edit
                </button>
            ),
        },
        {
            headerName: "Full Name",
            field: "fullName",
            colId: "fullName",
            sortable: true,
            filter: "agTextColumnFilter",
            floatingFilter: true
        },
        {
            headerName: "Admin ID",
            field: "adminId",
            colId: "admin.id",
            width: 120,
            sortable: true,
            filter: "agNumberColumnFilter",
            floatingFilter: true
        },
        {
            headerName: "Created",
            field: "creationDate",
            colId: "creationTime",
            width: 190,
            sortable: true,
            filter: "agDateColumnFilter",
            floatingFilter: true,
            valueFormatter: (p) => p.value ? new Date(p.value).toLocaleString() : "",
        },
        {
            headerName: "Vehicles",
            field: "vehiclesCount",
            // colId: "vehiclesCount",
            width: 130,
            sortable: false,
            filter: false,
            floatingFilter: false
        },
    ]), [onOpenEditPersonModal]);

    const mapSortModel = (sm = []) => sm.map(s => ({colId: s.colId, sort: s.sort}));

    const makeDatasource = useCallback(() => ({
        getRows: async (params) => {
            try {
                const body = {
                    startRow: params.startRow,
                    endRow: params.endRow,
                    sortModel: mapSortModel(params.sortModel),
                    filterModel: params.filterModel || {},
                };

                const res = await fetch(`${API_BASE}/api/person/query`, {
                    method: "POST",
                    credentials: "include",
                    headers: {"Content-Type": "application/json", "Accept": "application/json"},
                    body: JSON.stringify(body),
                });

                if (!res.ok) {
                    params.failCallback();
                    return;
                }
                const data = await res.json();
                params.successCallback(data.rows || [], data.lastRow ?? 0);
            } catch (e) {
                console.error(e);
                params.failCallback();
            }
        }
    }), []);

    const setDatasource = useCallback(() => {
        if (!gridApiRef.current) return;
        const ds = makeDatasource();
        gridApiRef.current.setGridOption('datasource', ds);
        gridApiRef.current.purgeInfiniteCache();
    }, [makeDatasource]);

    const exposeRefresh = useCallback(() => {
        onReadyRefresh?.(() => {
            if (!gridApiRef.current) return;
            gridApiRef.current.refreshInfiniteCache();
        });
    }, [onReadyRefresh]);

    const exposeControls = useCallback(() => {
        if (!onReadyControls) return;
        const api = gridApiRef.current;
        if (!api) return;

        onReadyControls({
            refresh: () => api.refreshInfiniteCache(),
            clearFilters: () => {
                api.setFilterModel(null);
                api.onFilterChanged();
                api.purgeInfiniteCache();
                api.ensureIndexVisible(0);
            },
            filterByName: (txt) => {
                api.setFilterModel({
                    fullName: {filterType: "text", type: "contains", filter: String(txt || "")}
                });
                api.onFilterChanged();
                api.purgeInfiniteCache();
                api.ensureIndexVisible(0);
            }
        });
    }, [onReadyControls]);

    const onGridReady = useCallback((e) => {
        gridApiRef.current = e.api;
        setDatasource();
        exposeRefresh();
        exposeControls();
    }, [setDatasource, exposeRefresh, exposeControls]);

    const onFilterChanged = useCallback(() => {
        gridApiRef.current?.purgeInfiniteCache();
        gridApiRef.current?.ensureIndexVisible(0);
    }, []);

    const onSortChanged = useCallback(() => {
        gridApiRef.current?.purgeInfiniteCache();
        gridApiRef.current?.ensureIndexVisible(0);
    }, []);

    return (
        <div style={{minWidth: "100%", height: 600}}>
            <AgGridReact
                theme={personTableTheme}
                columnDefs={colDefs}
                rowModelType="infinite"
                cacheBlockSize={50}
                maxBlocksInCache={2}
                pagination
                paginationPageSize={50}
                onGridReady={onGridReady}
                onFilterChanged={onFilterChanged}
                onSortChanged={onSortChanged}
                suppressMultiSort={false}
                defaultColDef={{filter: true, sortable: true, floatingFilter: true}}
            />
        </div>
    );
}
