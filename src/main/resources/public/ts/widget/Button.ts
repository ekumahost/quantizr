import { ReactNode } from "react";
import { Constants as C } from "../Constants";
import { PubSub } from "../PubSub";
import { Singletons } from "../Singletons";
import { Comp } from "./base/Comp";

let S: Singletons;
PubSub.sub(C.PUBSUB_SingletonsReady, (ctx: Singletons) => {
    S = ctx;
});

export class Button extends Comp {

    constructor(text: string, public callback: Function, _attribs: Object = null, moreClasses: string = "btn-secondary") {
        super(_attribs);
        S.util.mergeAndMixProps(this.attribs, {
            className: "btn " + moreClasses, /* also: secondary, info, success, danger, warning */
            type: "button"
        }, " ");

        this.attribs.onClick = callback;
        this.setText(text);
    }

    setText(text: string): void {
        this.mergeState({ text });
    }

    compRender(): ReactNode {
        let icon: any;
        if (this.attribs.iconclass) {
            icon = S.e("i", {
                key: "s_" + this.getId(),
                className: this.attribs.iconclass,
                style: {
                    marginRight: "6px"
                }
            });
        }

        if (this.getState().enabled) {
            delete this.attribs.disabled;
        }
        else {
            this.attribs.disabled = "disabled";
        }

        //console.log("ButtonRender: "+this.jsClassName+" attribs.disabled="+this.attribs.disabled);

        // I'd rather hide the button (instead of show as disabled), but the corners are inconisistent due to the way bootstrap 
        // jams buttons together and
        // makes them touch with square sides so that if a button is hidden from one of the left or right sides it leaves an ugly square edge
        // on remaining button.
        // Appears to be no clean solution:
        // https://stackoverflow.com/questions/16226268/hide-button-in-btn-group-twitter-bootstrap
        // https://stackoverflow.com/questions/28187567/how-to-ignore-hidden-elements-in-a-bootstrap-button-group
        // this.attribs.style = {
        //     display : this.getState().visible ? "inline-block" : "none"
        // };

        return S.e("button", this.attribs, [icon, this.getState().text]);
    }
}
