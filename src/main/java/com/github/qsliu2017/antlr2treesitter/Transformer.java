package com.github.qsliu2017.antlr2treesitter;

import java.util.*;
import java.util.stream.Collectors;

import com.github.qsliu2017.antlr2treesitter.parser.ANTLRv4Parser;
import com.github.qsliu2017.antlr2treesitter.parser.ANTLRv4Parser.GrammarSpecContext;
import com.github.qsliu2017.antlr2treesitter.parser.ANTLRv4Parser.ParserRuleSpecContext;
import com.github.qsliu2017.antlr2treesitter.parser.ANTLRv4ParserBaseListener;
import org.antlr.v4.runtime.RuleContext;

public class Transformer extends ANTLRv4ParserBaseListener {
    @Override
    public String toString() {
        return String.join(",\n", rules) + "\n";
    }

    @Override
    public void exitGrammarSpec(GrammarSpecContext ctx) {
        // TODO Auto-generated method stub
        super.exitGrammarSpec(ctx);
    }

    List<String> rules = new LinkedList<>();

    @Override
    public void exitLexerRuleSpec(ANTLRv4Parser.LexerRuleSpecContext ctx) {
        String ruleName = ctx.TOKEN_REF().getText();
        String rule = "$ => "
                + (ctx.lexerRuleBlock().lexerAltList().lexerAlt().size() > 1 ? "choice(" : "")
                + ctx.lexerRuleBlock().lexerAltList().lexerAlt().stream()
                .map(alt -> (alt.lexerElements().lexerElement().size() > 1 ? "seq(" : "")
                        + alt.lexerElements().lexerElement().stream()
                        .map(RuleContext::getText)
                        .collect(Collectors.joining(", "))
                        + (alt.lexerElements().lexerElement().size() > 1 ? ")" : "")
                )
                .collect(Collectors.joining(", "))
                + (ctx.lexerRuleBlock().lexerAltList().lexerAlt().size() > 1 ? ")" : "");
        rules.add(ruleName + ": " + rule);
    }

    @Override
    public void exitParserRuleSpec(ParserRuleSpecContext ctx) {
        String ruleName = ctx.RULE_REF().getText();
        String rule = "$ => "
                + (ctx.ruleBlock().ruleAltList().labeledAlt().size() > 1 ? "choice(" : "")
                + ctx.ruleBlock().ruleAltList().labeledAlt().stream()
                .map(alt -> (alt.alternative().element().size() > 1 ? "seq(" : "")
                        + alt.alternative().element().stream()
                        .map(this::rewriteElement)
                        .collect(Collectors.joining(", "))
                        + (alt.alternative().element().size() > 1 ? ")" : "")
                )
                .collect(Collectors.joining(", "))
                + (ctx.ruleBlock().ruleAltList().labeledAlt().size() > 1 ? ")" : "");
        rules.add(ruleName + ": " + rule);
    }

    private String rewriteElement(ANTLRv4Parser.ElementContext ctx) {
        assert ctx != null;
        if (ctx.labeledElement() != null) {
            ANTLRv4Parser.LabeledElementContext labeledElement = ctx.labeledElement();
            if (labeledElement.atom() == null) return "";
            String rewritten = "field('" + labeledElement.identifier().getText() + "', " + rewriteAtom(labeledElement.atom()) + ")";
            if (ctx.ebnfSuffix() != null) {
                if (ctx.ebnfSuffix().STAR() != null) {
                    return "repeat(" + rewritten + ")";
                } else if (ctx.ebnfSuffix().PLUS() != null) {
                    return "repeat1(" + rewritten + ")";
                } else if (ctx.ebnfSuffix().QUESTION() != null) {
                    return "optional(" + rewritten + ")";
                } else {
                    throw new IllegalArgumentException("Unknown ebnf suffix: " + ctx.ebnfSuffix().getText());
                }
            }
            return rewritten;
        } else if (ctx.atom() != null) {
            return rewriteAtom(ctx.atom());
        } else if (ctx.ebnf() != null) {
            ANTLRv4Parser.BlockContext block = ctx.ebnf().block();
            String rewritten = (block.altList().alternative().size() > 1 ? "choice(" : "")
                    + block.altList().alternative().stream()
                    .map(alt -> (alt.element().size() > 1 ? "seq(" : "")
                            + alt.element().stream().map(this::rewriteElement).collect(Collectors.joining(", "))
                            + (alt.element().size() > 1 ? ")" : ""))
                    .collect(Collectors.joining(", "))
                    + (block.altList().alternative().size() > 1 ? ")" : "");
            if (ctx.ebnf().blockSuffix() == null) return rewritten;
            ANTLRv4Parser.EbnfSuffixContext suffix = ctx.ebnf().blockSuffix().ebnfSuffix();
            if (suffix.STAR() != null) {
                return "repeat(" + rewritten + ")";
            } else if (suffix.PLUS() != null) {
                return "repeat1(" + rewritten + ")";
            } else if (suffix.QUESTION() != null) {
                return "optional(" + rewritten + ")";
            } else {
                throw new IllegalArgumentException("Unknown ebnf suffix: " + suffix.getText());
            }
        } else if (ctx.actionBlock() != null) {
            return "<action block>(" + ctx.getText() + ")";
        } else {
            throw new IllegalArgumentException("Unknown element type: " + ctx.getText());
        }
    }

    private String rewriteAtom(ANTLRv4Parser.AtomContext ctx) {
        assert ctx != null;
        if (ctx.terminalDef() != null) {
            if (ctx.terminalDef().TOKEN_REF() != null) {
                return "$." + ctx.terminalDef().TOKEN_REF().getText();
            } else if (ctx.terminalDef().STRING_LITERAL() != null) {
                return ctx.terminalDef().STRING_LITERAL().getText();
            } else {
                throw new IllegalArgumentException("Unknown terminal type: " + ctx.getText());
            }
        } else if (ctx.ruleref() != null) {
            return "$." + ctx.ruleref().RULE_REF().getText();
        } else if (ctx.notSet() != null) {
            return "not(" + ctx.getText() + ")";
        } else if (ctx.DOT() != null) {
            return "any";
        } else {
            throw new IllegalArgumentException("Unknown atom type: " + ctx.getText());
        }
    }
}
