import { appState, dispatch, fastDispatch } from "./AppRedux";
import { AppState } from "./AppState";
import { Constants as C } from "./Constants";
import { LoginDlg } from "./dlg/LoginDlg";
import { MessageDlg } from "./dlg/MessageDlg";
import { PrefsDlg } from "./dlg/PrefsDlg";
import { SearchContentDlg } from "./dlg/SearchContentDlg";
import { NavIntf } from "./intf/NavIntf";
import * as J from "./JavaIntf";
import { PubSub } from "./PubSub";
import { Singletons } from "./Singletons";
import { Anchor } from "./widget/Anchor";
import { Heading } from "./widget/Heading";
import { VerticalLayout } from "./widget/VerticalLayout";

let S: Singletons;
PubSub.sub(C.PUBSUB_SingletonsReady, (s: Singletons) => {
    S = s;
});

export class Nav implements NavIntf {
    _UID_ROWID_PREFIX: string = "row_";

    /* todo-2: eventually when we do paging for other lists, we will need a set of these variables for each list display (i.e. search, timeline, etc) */
    mainOffset: number = 0;

    /* todo-2: need to have this value passed from server rather than coded in TypeScript, however for now
    this MUST match RenderNodeService.ROWS_PER_PAGE in Java on server. */
    ROWS_PER_PAGE: number = 25;

    login = (state: AppState): void => {
        const dlg = new LoginDlg(null, state);
        dlg.populateFromLocalDb();
        dlg.open();
    }

    logout = (state: AppState): void => {
        S.user.logout(true, state);
    }

    signup = (state: AppState): void => {
        S.user.openSignupPg(state);
    }

    preferences = (state: AppState): void => {
        new PrefsDlg(state).open();
    }

    displayingRepositoryRoot = (state: AppState): boolean => {
        if (!state.node) return false;
        // one way to detect repository root (without path, since we don't send paths back to client) is as the only node that owns itself.
        // console.log(S.util.prettyPrint(S.meta64.currentNodeData.node));
        return state.node.id === state.node.ownerId;
    }

    displayingHome = (state: AppState): boolean => {
        if (!state.node) return false;
        if (state.isAnonUser) {
            return state.node.id === state.anonUserLandingPageNode;
        } else {
            return state.node.id === state.homeNodeId;
        }
    }

    parentVisibleToUser = (state: AppState): boolean => {
        return !this.displayingHome(state);
    }

    upLevelResponse = (res: J.RenderNodeResponse, id: string, scrollToTop: boolean, state: AppState): void => {
        if (!res || !res.node) {
            S.util.showMessage("No data is visible to you above this node.", "Warning");
        } else {
            S.render.renderPageFromData(res, scrollToTop, id, true, true, state);
        }
    }

    navOpenSelectedNode = (state: AppState): void => {
        const currentSelNode: J.NodeInfo = S.meta64.getHighlightedNode(state);
        if (!currentSelNode) return;
        S.nav.cached_openNodeById(currentSelNode.id, state);
    }

    navToPrev = () => {
        S.nav.navToSibling(-1);
    }

    navToNext = () => {
        S.nav.navToSibling(1);
    }

    navToSibling = (siblingOffset: number, state?: AppState): void => {
        state = appState(state);
        if (!state.node) return null;

        this.mainOffset = 0;
        S.util.ajax<J.RenderNodeRequest, J.RenderNodeResponse>("renderNode", {
            nodeId: state.node.id,
            upLevel: null,
            siblingOffset: siblingOffset,
            renderParentIfLeaf: true,
            offset: this.mainOffset,
            goToLastPage: false,
            forceIPFSRefresh: false,
            singleNode: false
        },
        // success callback
        (res: J.RenderNodeResponse) => {
            this.upLevelResponse(res, null, true, state);
        },
        // fail callback
        (res: string) => {
            this.navHome(state);
        });
    }

    navUpLevel = (event: any = null): void => {
        const state = appState();
        if (!state.node) return null;

        //Always just scroll to the top before doing an actual 'upLevel' to parent.
        if (S.view.docElm.scrollTop > 100) {
            S.view.docElm.scrollTop = 0;

            /* This works fine but actually for me causes eye-strain. I might enable this for mobile some day, but for now
            let's just comment it out. */
            //S.util.animateScrollToTop();

            S.meta64.highlightNode(state.node, false, state);
            return;
        }

        if (!this.parentVisibleToUser(state)) {
            S.util.showMessage("The parent of this node isn't shared to you.", "Warning");
            // Already at root. Can't go up.
            return;
        }

        S.util.ajax<J.RenderNodeRequest, J.RenderNodeResponse>("renderNode", {
            nodeId: state.node.id,
            upLevel: 1,
            siblingOffset: 0,
            renderParentIfLeaf: false,
            offset: this.mainOffset,
            goToLastPage: false,
            forceIPFSRefresh: false,
            singleNode: false
        },
        //success callback
        (res: J.RenderNodeResponse) => {
            this.mainOffset = res.offsetOfNodeFound;
            this.upLevelResponse(res, state.node.id, false, state);
        },
        //fail callback
        (res: string) => {
            //Navigating home was a bad idea. If someone tries to uplevel and cannot, we don't want to change them away from
            //whatever page they're on. Just show the error and stay on same node.
            //this.navHome(state);
        });
    }

    /*
     * turn of row selection DOM element of whatever row is currently selected
     */
    getSelectedDomElement = (state: AppState): HTMLElement => {
        var currentSelNode = S.meta64.getHighlightedNode(state);
        if (currentSelNode) {
            /* get node by node identifier */
            const node: J.NodeInfo = state.idToNodeMap[currentSelNode.id];

            if (node) {
                //console.log("found highlighted node.id=" + node.id);

                /* now make CSS id from node */
                const nodeId: string = this._UID_ROWID_PREFIX + node.id;
                // console.log("looking up using element id: "+nodeId);

                return S.util.domElm(nodeId);
            }
        }

        return null;
    }

    /* NOTE: Elements that have this as an onClick method must have the nodeId
    on an attribute of the element */
    cached_clickNodeRow = (nodeId: string, state?: AppState): void => {
        state = appState(state);

        /* First check if this node is already highlighted and if so just return */
        const highlightNode = S.meta64.getHighlightedNode();
        if (highlightNode && highlightNode.id === nodeId) {
            return;
        }

        const node: J.NodeInfo = state.idToNodeMap[nodeId];
        if (!node) {
            //console.log("idToNodeMap: "+S.util.prettyPrint(state.idToNodeMap));
            throw new Error("node not found in idToNodeMap: " + nodeId);
        }

        /*
         * sets which node is selected on this page (i.e. parent node of this page being the 'key')
         */
        S.meta64.highlightNode(node, false, state);

        /* We do this async just to make the fastest possible response when clicking on a node */
        setTimeout(() => {
            S.util.updateHistory(null, node, state);
        }, 10);

        fastDispatch({
            type: "Action_FastRefresh",
            updateNew: (s: AppState): AppState => {
                return { ...state };
            }
        });
    }

    openContentNode = (nodePathOrId: string, state: AppState): void => {
        this.mainOffset = 0;
        console.log("openContentNode()");
        S.util.ajax<J.RenderNodeRequest, J.RenderNodeResponse>("renderNode", {
            nodeId: nodePathOrId,
            upLevel: null,
            siblingOffset: 0,
            renderParentIfLeaf: null,
            offset: this.mainOffset,
            goToLastPage: false,
            forceIPFSRefresh: false,
            singleNode: false
        }, (res) => { this.navPageNodeResponse(res, state); });
    }

    cached_openNodeById = (id: string, state: AppState): void => {
        this.mainOffset = 0;

        state = appState(state);
        const node: J.NodeInfo = state.idToNodeMap[id];
        S.meta64.highlightNode(node, false, state);

        if (!node) {
            S.util.showMessage("Unknown nodeId in openNodeByUid: " + id, "Warning");
        } else {
            S.view.refreshTree(node.id, true, null, false, false, true, true, state);
        }
    }

    setNodeSel = (selected: boolean, id: string, state: AppState): void => {
        state = appState(state);
        if (selected) {
            state.selectedNodes[id] = true;
        } else {
            delete state.selectedNodes[id];
        }
    }

    navPageNodeResponse = (res: J.RenderNodeResponse, state: AppState): void => {
        console.log("navPageNodeResponse.");
        S.meta64.clearSelNodes(state);
        S.render.renderPageFromData(res, true, null, true, true, state);
        S.meta64.selectTab("mainTab");
    }

    geoLocation = (state: AppState): void => {
        if (navigator.geolocation) {
            navigator.geolocation.getCurrentPosition((location) => {
                new MessageDlg("Message", "Title", null,
                    new VerticalLayout([
                        new Heading(3, "Lat:" + location.coords.latitude),
                        new Heading(3, "Lon:" + location.coords.longitude),
                        new Heading(4, "+/- " + location.coords.accuracy),
                        new Anchor("https://www.google.com/maps/search/?api=1&query=" + location.coords.latitude + "," + location.coords.longitude,
                            "Show Your Google Maps Location",
                            { target: "_blank" })
                    ]), false, 0, state
                ).open();
            });
        }
        else {
            new MessageDlg("GeoLocation is not available on this device.", "Message", null, null, false, 0, state).open();
        }
    }

    showMainMenu = (state: AppState): void => {
        if (S.mainMenu) {
            S.mainMenu.open("inline-block");
        }
    }

    navFeed = (state: AppState): void => {
        S.srch.feed("~" + J.NodeType.FRIEND_LIST);
    }

    navHome = (state: AppState): void => {
        console.log("navHome()");
        if (state.isAnonUser) {
            S.meta64.loadAnonPageHome(state);
        } else {
            this.mainOffset = 0;
            S.util.ajax<J.RenderNodeRequest, J.RenderNodeResponse>("renderNode", {
                nodeId: state.homeNodeId,
                upLevel: null,
                siblingOffset: 0,
                renderParentIfLeaf: null,
                offset: this.mainOffset,
                goToLastPage: false,
                forceIPFSRefresh: false,
                singleNode: false
            }, (res) => { this.navPageNodeResponse(res, state); });
        }
    }

    navPublicHome = (state: AppState): void => {
        S.meta64.loadAnonPageHome(state);
    }

    runSearch = (): void => {
        const state = appState();
        this.cached_clickNodeRow(state.node.id);
        new SearchContentDlg(state).open();
    }

    runTimeline = (): void => {
        const state = appState();
        this.cached_clickNodeRow(state.node.id);
        S.srch.timeline("mtm", state);
    }

    closeFullScreenImgViewer = (appState: AppState): void => {
        dispatch({
            type: "Action_CloseFullScreenImgViewer",
            update: (s: AppState): void => {
                s.fullScreenViewId = null;
                s.fullScreenGraphId = null;
            }
        });
    }

    prevFullScreenImgViewer = (appState: AppState): void => {
        const prevNode: J.NodeInfo = this.getAdjacentNode("prev", appState);

        if (prevNode) {
            dispatch({
                type: "Action_PrevFullScreenImgViewer",
                update: (s: AppState): void => {
                    s.fullScreenViewId = prevNode.id;
                }
            });
        }
    }

    nextFullScreenImgViewer = (appState: AppState): void => {
        const nextNode: J.NodeInfo = this.getAdjacentNode("next", appState);

        if (nextNode) {
            dispatch({
                type: "Action_NextFullScreenImgViewer",
                update: (s: AppState): void => {
                    s.fullScreenViewId = nextNode.id;
                }
            });
        }
    }

    //todo-1: need to make view.scrollRelativeToNode use this function instead of embedding all the same logic.
    getAdjacentNode = (dir: string, state: AppState): J.NodeInfo => {

        let newNode: J.NodeInfo = null;

        //First detect if page root node is selected, before doing a child search
        if (state.fullScreenViewId === state.node.id) {
            return null;
        }
        else if (state.node.children && state.node.children.length > 0) {
            let prevChild = null;
            let nodeFound = false;

            state.node.children.some((child: J.NodeInfo) => {
                let ret = false;
                const isAnAccountNode = child.ownerId && child.id === child.ownerId;

                if (S.props.hasBinary(child) && !isAnAccountNode) {

                    if (nodeFound && dir === "next") {
                        ret = true;
                        newNode = child;
                    }

                    if (child.id === state.fullScreenViewId) {
                        if (dir === "prev") {
                            if (prevChild) {
                                ret = true;
                                newNode = prevChild;
                            }
                        }
                        nodeFound = true;
                    }
                    prevChild = child;
                }
                //NOTE: returning true stops the iteration.
                return ret;
            });
        }

        return newNode;
    }
}
