; This is the Calva evaluation results output window.
; TIPS: The keyboard shortcut `ctrl+alt+c o` shows and focuses this window
;   when connected to a REPL session.
; Please see https://calva.io/output/ for more info.
; Happy coding! ♥️

; Jacking in...
; Hooking up nREPL sessions...
; Connected session: clj
; TIPS: 
;   - You can edit the contents here. Use it as a REPL if you like.
;   - `alt+enter` evaluates the current top level form.
;   - `ctrl+enter` evaluates the current form.
;   - `alt+up` and `alt+down` traverse up and down the REPL command history
;      when the cursor is after the last contents at the prompt
;   - Clojure lines in stack traces are peekable and clickable.
clj::user=> 

; Jack-in done.
clj::user=> 
; Evaluating file: sandbox.clj
nil
clj::sandbox=> 
#datahike/DB {:max-tx 536870912 :max-eid 0}

clj::sandbox=> 
#'sandbox/with

clj::sandbox=> 
#'sandbox/bob-db

clj::sandbox=> 
-collect -> acc [#object[[Ljava.lang.Object; 0x3a789e20 [Ljava.lang.Object;@3a789e20]]
-collect -> rels [#datahike.query.Relation{:attrs {?e 0, ?a 1}, :tuples [#object[[Ljava.lang.Object; 0x414ea4f3 [Ljava.lang.Object;@414ea4f3]]}]
-collect -> symbols (?a)
-collect -> acc (#object[[Ljava.lang.Object; 0x389ff573 [Ljava.lang.Object;@389ff573])
-collect -> rels nil
-collect -> symbols (?a)
#{[5]}

clj::sandbox=> 
; Evaluating file: query.cljc
#multifn[q 0x891a5bd]
clj::datahike.query=> 
; Evaluating file: sandbox.clj
nil
clj::sandbox=> 
#datahike.query.Context{:rels [#datahike.query.Relation{:attrs {?e 0, ?a 1}, :tuples [#object[[Ljava.lang.Object; 0x53d38c01 [Ljava.lang.Object;@53d38c01]]}], :sources {$ #datahike/DB {:max-tx 536870913 :max-eid 1}}, :rules {}}
-collect -> acc [#object[[Ljava.lang.Object; 0x7859ec21 [Ljava.lang.Object;@7859ec21]]
-collect -> rels [#datahike.query.Relation{:attrs {?e 0, ?a 1}, :tuples [#object[[Ljava.lang.Object; 0x53d38c01 [Ljava.lang.Object;@53d38c01]]}]
-collect -> symbols (?a)
-collect -> acc (#object[[Ljava.lang.Object; 0x6a7c6f6a [Ljava.lang.Object;@6a7c6f6a])
-collect -> rels nil
-collect -> symbols (?a)
#{[5]}

clj::sandbox=> 
