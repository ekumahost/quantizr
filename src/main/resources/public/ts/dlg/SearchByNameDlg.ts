import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { ButtonBar } from "../widget/ButtonBar";
import { Button } from "../widget/Button";
import { TextField } from "../widget/TextField";
import { TextContent } from "../widget/TextContent";
import { PubSub } from "../PubSub";
import { Constants as C} from "../Constants";
import { Singletons } from "../Singletons";
import { Form } from "../widget/Form";

let S : Singletons;
PubSub.sub(C.PUBSUB_SingletonsReady, (ctx: Singletons) => {
    S = ctx;
});

export class SearchByNameDlg extends DialogBase {

    static defaultSearchText: string = "";
    searchTextField: TextField;
  
    constructor() {
        super("Search by Node Name", "app-modal-content-medium-width");
        
        this.setChildren([
            new Form(null, [
                new TextContent("All sub-nodes under the selected node will be searched."),
                this.searchTextField = new TextField("Node Name", {
                    onKeyPress : (e: KeyboardEvent) => { 
                        if (e.which == 13) { // 13==enter key code
                            this.search();
                            return false;
                        }
                    }
                }, SearchByNameDlg.defaultSearchText),
                new ButtonBar([
                    new Button("Search", this.search, null, "primary"),
                    new Button("Close", () => {
                        this.close();
                    })
                ])
            ])
        ]);
    }

    search = () => {
        if (!S.util.ajaxReady("searchNodes")) {
            return;
        }

        // until we have better validation
        let node = S.meta64.getHighlightedNode();
        if (!node) {
            S.util.showMessage("No node is selected to search under.");
            return;
        }

        // until better validation, just check for empty
        let searchText = this.searchTextField.getValue();
        if (!searchText) {
            S.util.showMessage("Enter search text.");
            return;
        }

        SearchByNameDlg.defaultSearchText = searchText;

        S.util.ajax<J.NodeSearchRequest, J.NodeSearchResponse>("nodeSearch", {
            "nodeId": node.id,
            "searchText": searchText,
            "sortDir": "",
            "sortField": "",
            "searchProp": "node.name"
        }, this.searchNodesResponse);
    }

    searchNodesResponse = (res: J.NodeSearchResponse) => {
        S.srch.searchNodesResponse(res);
        this.close();
    }

    init = (): void => {
        this.searchTextField.focus();
    }
}

