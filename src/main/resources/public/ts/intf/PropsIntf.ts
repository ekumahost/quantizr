import * as J from "../JavaIntf";
import { PropTable } from "../widget/PropTable";

//todo-0: methods in here named with 'property' should be shortened to 'prop'
export interface PropsIntf {
    orderProps(propOrder: string[], _props: J.PropertyInfo[]): J.PropertyInfo[];
    moveNodePosition(props: J.PropertyInfo[], idx: number, typeName: string): number;
    propsToggle(): void;
    deleteProp(node: J.NodeInfo, propertyName : string): void;
    renderProperties(properties : J.PropertyInfo[]): PropTable;
    getNodeProp(propertyName: string, node: J.NodeInfo): J.PropertyInfo;
    getNodePropVal(propertyName : string, node: J.NodeInfo): string;
    setNodePropVal(propertyName : string, node: J.NodeInfo, val: string): void;
    isEncrypted(node: J.NodeInfo): boolean;
}
