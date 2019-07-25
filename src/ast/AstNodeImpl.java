package ast;

import lexer.Token;

import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author dejavudwh isHudw
 */

public class AstNodeImpl extends HashMap<NodeKey, Object> implements AstNode {
    private Token type;
    private AstNodeImpl parent;
    private ArrayList<AstNode> children;
    String   name;

    public AstNodeImpl(Token type) {
        this.type = type;
        this.parent = null;
        this.children = new ArrayList<>();
        setAttribute(NodeKey.TokenType, type);
    }

    @Override
    public AstNode addChild(AstNode node) {
        if (node != null) {
            children.add(node);
            ((AstNodeImpl)node).parent = this;
        }

        return node;
    }

    @Override
    public AstNode getParent() {
        return parent;
    }

    @Override
    public ArrayList<AstNode> getChildren() {
        return children;
    }

    @Override
    public void setAttribute(NodeKey key, Object value) {
        if (key == NodeKey.TEXT) {
            name = (String)value;
        }
        put(key, value);
    }

    @Override
    public Object getAttribute(NodeKey key) {
        return get(key);
    }

    @Override
    public String toString() {
        String info = "";
        if (get(NodeKey.VALUE) != null) {
            info += "Node Value is " + get(NodeKey.VALUE).toString();
        }

        if (get(NodeKey.TEXT) != null) {
            info += "\nNode Text is " + get(NodeKey.TEXT).toString();
        }

        if (get(NodeKey.SYMBOL) != null) {
            info += "\nNode Symbol is " + get(NodeKey.SYMBOL).toString();
        }

        return info  + "\n Node Type is " + type.toString();
    }

}
