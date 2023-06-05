.text
.global  _start
.type   main, @function
_start:
    la      a5, data_section
    la      a6, data_section_t
    vle32.v v0,(a5)
    vle32.v v1,(a6)
    vmul.vv v2,v1,v0

.data
data_section:
    .word   1
    .word   2
    .word   3
    .word   4

.data
data_section_t:
    .word   5
    .word   6
    .word   7
    .word   8