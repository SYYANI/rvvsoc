ARCH_PREF := riscv64-unknown-elf-
GCC := $(ARCH_PREF)gcc
LD := $(ARCH_PREF)ld
OBJDUMP := $(ARCH_PREF)objdump
OBJCOPY := $(ARCH_PREF)objcopy
MARCH := rv32iv
CFLAGS := -DWITH_SMP
START_ADR := 0x00000000
ASM_FILE := relu.S
OBJ_FILE := $(ASM_FILE:.S=.o)

$(ASM_FILE:.S=.hex): $(ASM_FILE:.S=.bin)
	xxd -c 1 -p $(ASM_FILE:.S=.bin) > ../prog/$(ASM_FILE:.S=.hex)
	rm -f $(ASM_FILE:.S=.bin)

$(ASM_FILE:.S=.bin): out.o
	$(OBJCOPY) -O binary out.o $(ASM_FILE:.S=.bin)
	rm -f $(OBJ_FILE) out.o
	cp $(ASM_FILE:.S=.bin) ../prog/

out.o: $(OBJ_FILE)
	$(LD)  -m elf32lriscv -nostdlib -N -e _start -Ttext $(START_ADR) $(OBJ_FILE) -o out.o

$(OBJ_FILE): $(ASM_FILE)
	$(GCC) -c -march=$(MARCH) $(CFLAGS) -mabi=ilp32 -fno-builtin -o $(OBJ_FILE) $(ASM_FILE)

.PHONY: all clean

clean:
	rm -rf out.o $(OBJ_FILE)
