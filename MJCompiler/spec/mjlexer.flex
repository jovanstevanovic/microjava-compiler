package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;

%%

%{

	private Symbol new_symbol(int type) {
		return new Symbol(type, yyline+1, yycolumn);
	}
	
	private Symbol new_symbol(int type, Object value) {
		return new Symbol(type, yyline+1, yycolumn, value);
	}

%}

%cup
%line
%column

%xstate COMMENT

%eofval{
	return new_symbol(sym.EOF);
%eofval}

%%

<YYINITIAL> {

	" " 	{ }
	"\b" 	{ }
	"\t" 	{ }
	"\r\n" 	{ }
	"\f" 	{ }

	"program"   { return new_symbol(sym.PROGRAM, yytext()); }
	"print" 	{ return new_symbol(sym.PRINT, yytext()); }
	"new"		{ return new_symbol(sym.NEW, yytext()); }
	"read"		{ return new_symbol(sym.READ, yytext()); }
	"const"		{ return new_symbol(sym.CONST, yytext()); }
	"enum"		{ return new_symbol(sym.ENUM, yytext()); }
	"void"		{ return new_symbol(sym.VOID, yytext()); }
	"return"	{ return new_symbol(sym.RETURN, yytext()); }
	
	";"			{ return new_symbol(sym.SEMICOLON, yytext()); }
	","			{ return new_symbol(sym.COMMA, yytext()); }
	
	"."			{ return new_symbol(sym.DOT, yytext()); }
	
	"["			{ return new_symbol(sym.LBRACKET, yytext()); }
	"]"			{ return new_symbol(sym.RBRACKET, yytext()); }
	"("			{ return new_symbol(sym.LPARENT, yytext()); }
	")"			{ return new_symbol(sym.RPARENT, yytext()); }
	"{"			{ return new_symbol(sym.LBRACE, yytext()); }
	"}"			{ return new_symbol(sym.RBRACE, yytext()); }
	
	"++"		{ return new_symbol(sym.INC, yytext()); }
	"--"		{ return new_symbol(sym.DEC, yytext()); }
	"*"			{ return new_symbol(sym.MUL, yytext()); }
	"/"			{ return new_symbol(sym.DIV, yytext()); }
	"%"			{ return new_symbol(sym.MOD, yytext()); }
	"+"			{ return new_symbol(sym.ADD, yytext()); }
	"-"			{ return new_symbol(sym.SUB, yytext()); }
	"="			{ return new_symbol(sym.ASSIGN, yytext()); }
	
	[0-9]+				{ return new_symbol(sym.NUM, new Integer(yytext())); }
	
	"'"[\040-\176]"'"	{ return new_symbol(sym.CHAR, new Character(yytext().charAt(1))); }
	
	"true" | "false"	{ return new_symbol(sym.BOOL, new Boolean(yytext())); }
	
	([a-z]|[A-Z])[a-z|A-Z|0-9|_]*	{ return new_symbol(sym.IDENT, yytext()); }
	
	"//"		{ yybegin(COMMENT); }

	. { System.err.println("Leksicka greska ("+yytext()+") u liniji "+(yyline+1)); }
}

<COMMENT> {
	"\r\n"	{ yybegin(YYINITIAL); 	}
	.		{ yybegin(COMMENT); 	}
}








