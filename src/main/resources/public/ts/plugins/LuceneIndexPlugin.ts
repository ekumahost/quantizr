
// let S: Singletons;
// PubSub.sub(C.PUBSUB_SingletonsReady, (ctx: Singletons) => {
//     S = ctx;
// });

//todo-1: disabled for now.

// export class LuceneIndexTypeHandler implements TypeHandlerIntf {
//     constructor(private luceneIndexPlugin: LuceneIndexPlugin) {
//     }

//     render = (node: J.NodeInfo, rowStyling: boolean): Comp => {
//         let name = node.content;

//         let vertLayout = new VerticalLayout([
//             new Heading(3, name, {
//                 style:
//                 {
//                     marginLeft: "15px",
//                     marginTop: "15px"
//                 }
//             }),
//             new ButtonBar([
//                 new Button("Reindex", () => { this.luceneIndexPlugin.reindexNodeButton(node) }, {
//                     className: "bash-exec-button"
//                 }),
//                 new Button("Search", () => { this.luceneIndexPlugin.search(node) }, {
//                     className: "bash-exec-button"
//                 })])
//         ]);
//         return vertLayout;
//     }

//     orderProps(node: J.NodeInfo, _props: J.PropertyInfo[]): J.PropertyInfo[] {
//         return _props;
//     }

//     getIconClass(node: J.NodeInfo): string {
//         //https://www.w3schools.com/icons/fontawesome_icons_webapp.asp
//         return "fa fa-binoculars fa-lg";
//     }

//     allowAction(action: string): boolean {
//         return true;
//     }
// }

// export class LuceneIndexPlugin implements LuceneIndexPluginIntf {
//     luceneIndexTypeHandler: TypeHandlerIntf = new LuceneIndexTypeHandler(this);

//     init = () => {
//         S.plugin.addTypeHandler("luceneIndex", this.luceneIndexTypeHandler);
//     }

//     reindexNodeButton = (node: J.NodeInfo): void => {
//         let searchDir = S.props.getNodePropVal("searchDir", node);
//         if (!searchDir) {
//             alert("no searchDir property specified.");
//             return;
//         }

//         S.util.ajax<J.LuceneIndexRequest, J.LuceneIndexResponse>("luceneIndex", {
//             "nodeId": node.id,
//             "path": searchDir
//         }, this.executeNodeResponse);
//     }

//     search = (node: J.NodeInfo): void => {
//         new SearchFileSystemDlg().open();
//     }

//     private executeNodeResponse = (res: J.LuceneIndexResponse): void => {
//         console.log("ExecuteNodeResponse running.");

//         S.util.checkSuccess("Execute Node", res);
//         S.util.showMessage(res.message, true, "modal-lg");

//         // S.view.refreshTree(null, false);
//         // S.meta64.selectTab("mainTab");
//         // S.view.scrollToSelectedNode(null);
//     }
// }
