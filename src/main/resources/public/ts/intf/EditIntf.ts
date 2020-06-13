import * as I from "../Interfaces";
import * as J from "../JavaIntf";
import { AppState } from "../AppState";

export interface EditIntf {
    showReadOnlyProperties: boolean;

    saveClipboardToNode(): void;
    splitNode(splitType: string, delimiter: string, state: AppState): void;
    openProfileDlg(state: AppState): void;
    openChangePasswordDlg(state: AppState): void;
    openManageAccountDlg(state: AppState): void;
    editPreferences(state: AppState): void;
    openImportDlg(state: AppState): void;
    openExportDlg(state: AppState): void;
    isEditAllowed(node: any, state: AppState): boolean;
    isInsertAllowed(node: any, state: AppState): boolean;
    startEditingNewNode(typeName: string, createAtTop: boolean, parentNode: J.NodeInfo, nodeInsertTarget: J.NodeInfo, ordinalOffset: number, state: AppState): void;
    insertNodeResponse(res: J.InsertNodeResponse, state: AppState): void;
    createSubNodeResponse(res: J.CreateSubNodeResponse, state: AppState): void;
    saveNodeResponse(node: J.NodeInfo, res: J.SaveNodeResponse, state: AppState): void;
    toggleEditMode(state: AppState): void;
    cached_moveNodeUp(id: string, state?: AppState): void;
    cached_moveNodeDown(id: string, state?: AppState): void;
    moveNodeToTop(id: string, state: AppState): void;
    moveNodeToBottom(id: string, state: AppState): void;
    getFirstChildNode(state: AppState): any;
    cached_runEditNode(id: any, state?: AppState): void;
    insertNode(id: string, typeName: string, ordinalOffset: number, state?: AppState): void;
    cached_toolbarInsertNode(id: string): void;
    createSubNode(id: any, typeName: string, createAtTop: boolean, parentNode: J.NodeInfo, state: AppState): void;
    selectAllNodes(state: AppState) : void;
    cached_softDeleteSelNodes(nodeId: string);
    deleteSelNodes(nodeId: string, hardDelete: boolean, state?: AppState): void;
    getBestPostDeleteSelNode(state: AppState): J.NodeInfo;
    cached_cutSelNodes(nodeId: string, state?: AppState): void;
    undoCutSelNodes(state: AppState): void;
    cached_pasteSelNodesInside(nodeId: string);
    pasteSelNodes(nodeId: string, location: string, state?: AppState): void;
    cached_pasteSelNodes_InlineEnd(nodeId: string);
    cached_pasteSelNodes_InlineAbove(nodeId: string);
    cached_pasteSelNodes_Inline(nodeId: string);
    insertBookWarAndPeace(state: AppState): void;
    emptyTrash(state: AppState): void;
    clearInbox(state: AppState): void;
    cached_newSubNode(id: any);
    addComment(node: J.NodeInfo, state: AppState): void;
    addFriend(node: J.NodeInfo, state: AppState): void;
}

