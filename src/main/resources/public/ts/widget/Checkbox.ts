import { ReactNode } from "react";
import { Constants as C } from "../Constants";
import * as I from "../Interfaces";
import { ValueIntf } from "../Interfaces";
import { PubSub } from "../PubSub";
import { Singletons } from "../Singletons";
import { Comp } from "./base/Comp";

let S: Singletons;
PubSub.sub(C.PUBSUB_SingletonsReady, (ctx: Singletons) => {
    S = ctx;
});

export class Checkbox extends Comp implements I.CheckboxIntf {

    constructor(public label: string = null, _attribs: Object = null, private valueIntf: ValueIntf) {
        super(_attribs);

        this.attribs.type = "checkbox";

        this.attribs.onChange = (evt: any) => {

            this.updateValFunc(evt.target.checked);
            
            // oops the renderCachedChildren is only for text input! where we KNOW we don't want the rest of the page rendering while user is typing.
            // and if you use this technique it WILL stop any other page updates from happening inside here, before the timer completes.
            // Comp.renderCachedChildren = true;
            // try {
            //     //console.log("e.target.checked=" + evt.target.checked);
            //     this.updateValFunc(evt.target.checked);
            // }
            // finally {
            //     /* React doesn't have a 'global' way to know when all rendering that's about to be done HAS been done, so all we can do here, is
            //     use a timeout */
            //     setTimeout(() => {
            //         Comp.renderCachedChildren = false;
            //     }, 250);
            // }
        };
    }

    //Handler to update state
    updateValFunc(value: boolean): void {
        if (value !== this.valueIntf.getValue()) {
            this.valueIntf.setValue(value);

            //needing this line took a while to figure out. If nothing is setting any actual detectable state change
            //during his call we have to do this here.
            this.forceRender();
        }
    }

    setChecked(val: boolean): void {
        this.valueIntf.setValue(val);
    }

    getChecked(): boolean {
        return this.valueIntf.getValue();
    }

    compRender(): ReactNode {
        //double-bang is important here becasue we do need to support the 'getvalue' comming back as null, or undefined, and in all cases
        //convert that to exactly the value 'true' or else React itself (internal to React) will fail
        this.attribs.checked = !!this.valueIntf.getValue();

        if (this.label) {
            return S.e("span", { key: this.attribs.id + "_span" }, S.e("input", this.attribs),
                S.e("label", {
                    key: this.attribs.id + "_label",
                    className: "checkbox-label",
                    htmlFor: this.attribs.id
                }, this.label));
        }
        else {
            return S.e("span", { key: this.attribs.id + "_span" }, S.e("input", this.attribs));
        }
    }
}
