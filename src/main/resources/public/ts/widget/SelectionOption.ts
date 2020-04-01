import { Comp } from "./base/Comp";
import { Singletons } from "../Singletons";
import { PubSub } from "../PubSub";
import { Constants as C} from "../Constants";
import { ReactNode } from "react";

let S : Singletons;
PubSub.sub(C.PUBSUB_SingletonsReady, (ctx: Singletons) => {
    S = ctx;
});

export class SelectionOption extends Comp {
    constructor(public key: string, public val : string, public selected: boolean=false) {
        super(null);
        this.attribs.value = this.key;
        if (selected) {
            this.attribs.selected = "selected";
        }
    }

    compRender = (): ReactNode => {
        return S.e('option', this.attribs, this.val);
    }
}
