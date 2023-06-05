# RVVSoC

Here is a RISC-V Vector Instruction Set SoC implemented using Chisel3.

### Did it work?

You should now have a working Chisel3 project.

You can run the included test with:
```sh
sbt test
```

You should see a whole bunch of output that ends with something like the following lines
```
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 5 s, completed May 16, 2023 12:18:44 PM
```
If you see the above then...

### It worked!

## Structure

The project is organized as follows:

<img src="http://flopsyyan-typora.oss-cn-beijing.aliyuncs.com/img/overlook.png" alt="overlook" style="zoom:50%;" />

And the SoC structure:

<img src="http://flopsyyan-typora.oss-cn-beijing.aliyuncs.com/img/socoverlook.png" alt="socoverlook" style="zoom:50%;" />


## License

RVVSoC is under the [BSD-2-Clause license](https://github.com/SYYANI/rvvsoc/blob/main/LICENSE). See the [LICENSE](./LICENSE) file for details.