import * as J from "./JavaIntf";
import { SearchIntf } from "./intf/SearchIntf";
import { Singletons } from "./Singletons";
import { PubSub } from "./PubSub";
import { Constants as C } from "./Constants";
import { Button } from "./widget/Button";
import { Div } from "./widget/Div";
import { Comp } from "./widget/base/Comp";
import { HorizontalLayout } from "./widget/HorizontalLayout";
import { Img } from "./widget/Img";
import { NodeCompContent } from "./comps/NodeCompContent";
import { AppState } from "./AppState";
import { dispatch } from "./AppRedux";
import { NodeCompRowHeader } from "./comps/NodeCompRowHeader";
import { NodeCompRowFooter } from "./comps/NodeCompRowFooter";

let S: Singletons;
PubSub.sub(C.PUBSUB_SingletonsReady, (s: Singletons) => {
    S = s;
});

export class Search implements SearchIntf {

    _UID_ROWID_PREFIX: string = "srch_row_";

    searchNodes: any = null;
    searchText: string = null;

    searchOffset = 0;
    timelineOffset = 0;

    /*
     * Will be the last row clicked on (NodeInfo.java object) and having the red highlight bar
     */
    highlightRowNode: J.NodeInfo = null;

    idToNodeMap: { [key: string]: J.NodeInfo } = {};

    numSearchResults = (res: J.NodeSearchResponse): number => {
        return res != null && //
            res.searchResults != null && //
            res.searchResults.length != null ? //
            res.searchResults.length : 0;
    }

    searchNodesResponse = (res: J.NodeSearchResponse) => {
        dispatch({
            type: "Action_RenderSearchResults",
            update: (s: AppState): void => {
                s.searchResults = res.searchResults;
            }
        });

        S.meta64.selectTab("searchTab");
    }

    timelineResponse = (res: J.NodeSearchResponse) => {
        dispatch({
            type: "Action_RenderTimelineResults",
            update: (s: AppState): void => {
                s.timelineResults = res.searchResults;
            }
        });
        S.meta64.selectTab("timelineTab");
    }

    searchFilesResponse = (res: J.FileSearchResponse, state: AppState) => {
        S.nav.mainOffset = 0;
        S.util.ajax<J.RenderNodeRequest, J.RenderNodeResponse>("renderNode", {
            nodeId: res.searchResultNodeId,
            upLevel: null,
            siblingOffset: 0,
            renderParentIfLeaf: null,
            offset: 0,
            goToLastPage: false,
            forceIPFSRefresh: false
        }, (res) => { S.nav.navPageNodeResponse(res, state) });
    }

    /* prop = mtm (modification time) | ctm (create time) */
    timeline = (prop: string, state: AppState) => {
        let node = S.meta64.getHighlightedNode(state);
        if (!node) {
            S.util.showMessage("No node is selected to 'timeline' under.", "Warning");
            return;
        }

        S.util.ajax<J.NodeSearchRequest, J.NodeSearchResponse>("nodeSearch", {
            nodeId: node.id,
            searchText: "",
            sortDir: "DESC",
            sortField: prop,
            searchProp: null,
            fuzzy: false,
            caseSensitive: false,
            searchDefinition: ""
        }, this.timelineResponse);
    }

    feed = (nodeId: string) => {
        S.util.ajax<J.NodeFeedRequest, J.NodeFeedResponse>("nodeFeed", {
            nodeId
        }, this.feedResponse);
    }

    feedResponse = (res: J.NodeFeedResponse) => {
        dispatch({
            type: "Action_RenderFeedResults",
            update: (s: AppState): void => {
                s.feedResults = res.searchResults;
            }
        });
        S.meta64.selectTab("feedTab");
    }

    initSearchNode = (node: J.NodeInfo) => {
        this.idToNodeMap[node.id] = node;
    }

    /*
     * Renders a single line of search results on the search results page.
     *
     * node is a NodeInfo.java JSON
     */
    renderSearchResultAsListItem = (node: J.NodeInfo, index: number, count: number, rowCount: number, allowAvatar: boolean, prefix: string, isFeed: boolean, state: AppState): Comp => {

        let cssId = this._UID_ROWID_PREFIX + node.id;
        let buttonBar = this.makeButtonBarHtml(node, allowAvatar, state);
        let content = new NodeCompContent(node, true, true, prefix, true);

        let clazz = "node-table-row";
        if (state.userPreferences.editMode) {
            clazz += " editing-border";
        }
        else {
            clazz += " non-editing-border"
        }

        if (S.render.fadeInId == node.id) { 
            S.render.fadeInId = null;
            clazz += " fadeInRowBkgClz";
        }

        return new Div(null, {
            className: clazz + " inactive-row",
            onClick: S.meta64.getNodeFunc(this.cached_clickOnSearchResultRow, "S.srch.clickOnSearchResultRow", node.id),
            id: cssId
        }, [
            new NodeCompRowHeader(node, isFeed),
            buttonBar, content,
            isFeed ? new NodeCompRowFooter(node, isFeed) : null
        ]);
    }

    makeButtonBarHtml = (node: J.NodeInfo, allowAvatar: boolean, state: AppState): Comp => {
        let avatarImg: Img = null;
        if (allowAvatar && node.owner != J.PrincipalName.ADMIN /* && S.props.getNodePropVal(J.NodeProp.BIN, node) */) {
            avatarImg = S.render.makeAvatarImage(node, state);
        }

        return new HorizontalLayout([avatarImg, new Button("Jump", () => {
            this.clickSearchNode(node.id, state);
        }, {
            title: "Jump to this Node in the Main Tab",
            id: "go-" + node.id
        }, "btn-secondary marginLeft")], "marginTop marginLeft");
    }

    cached_clickOnSearchResultRow = (id: string) => {
        //wow this implementation is obsolete.
        this.setRowHighlight(false);
        this.highlightRowNode = this.idToNodeMap[id];
        this.setRowHighlight(true);
    }

    clickSearchNode = (id: string, state: AppState) => {

        /*
         * update highlight node to point to the node clicked on, just to persist it for later
         */
        this.highlightRowNode = this.idToNodeMap[id];
        if (!this.highlightRowNode) {
            throw "Unable to find uid in search results: " + id;
        }

        S.view.refreshTree(this.highlightRowNode.id, true, this.highlightRowNode.id, false, false, true, true, state);
        S.meta64.selectTab("mainTab");
    }

    /*
     * turn of row selection styling of whatever row is currently selected
     */
    setRowHighlight = (state: boolean) => {

        if (!this.highlightRowNode) {
            return;
        }

        /* now make CSS id from node */
        let nodeId = this._UID_ROWID_PREFIX + this.highlightRowNode.id;

        let elm: HTMLElement = S.util.domElm(nodeId);
        if (elm) {
            /* change class on element */
            S.util.changeOrAddClassToElm(elm,
                state ? "inactive-row" : "active-row",
                state ? "active-row" : "inactive-row");
        }
    }
}
