import { Div } from "./Div";

// let S : Singletons;
// PubSub.sub(C.PUBSUB_SingletonsReady, (ctx: Singletons) => {
//     S = ctx;
// });

export class EditPropsTable extends Div {
    constructor(attribs : Object = {}) {
        super(null, attribs);
    }
}
