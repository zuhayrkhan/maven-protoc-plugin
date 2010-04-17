package com.google.protobuf.maven;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.File;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.*;
import static com.google.common.collect.Lists.newLinkedList;
import static com.google.common.collect.Sets.newHashSet;

/**
 * This class represents an invokable configuration of the {@code protoc}
 * compiler. The actual executable is invoked using the plexus
 * {@link Commandline}.
 * <p/>
 * This class currently only supports generating java source files.
 *
 * @author gak@google.com (Gregory Kick)
 */
final class Protoc {
    private final String executable;
    private final ImmutableSet<File> protoPathElements;
    private final ImmutableSet<File> protofiles;
    private final File javaOutputDirectory;
    private final CommandLineUtils.StringStreamConsumer error, output;
    private String protocPluginNameAndPath;
    private final ImmutableSet<String> additionalOutputPaths;

    /**
     * Constructs a new instance. This should only be used by the {@link Builder}.
     *
     * @param executable          The path to the {@code protoc} executable.
     * @param protocPlugin        The plugin to be specified for use with protoc
     * @param protoPath           The directories in which to search for imports.
     * @param protoFiles          The proto source files to compile.
     * @param additionalOutputs   Any additional outputs required
     * @param javaOutputDirectory The directory into which the java source files
     */
    private Protoc(String executable, String protocPlugin, ImmutableSet<File> protoPath,
                   ImmutableSet<File> protoFiles, ImmutableSet<String> additionalOutputs, File javaOutputDirectory) {
        this.additionalOutputPaths = additionalOutputs;
        this.executable = checkNotNull(executable, "executable");
        this.protocPluginNameAndPath = protocPlugin;
        this.protoPathElements = checkNotNull(protoPath, "protoPath");
        this.protofiles = checkNotNull(protoFiles, "protoFiles");
        this.javaOutputDirectory = checkNotNull(javaOutputDirectory, "javaOutputDirectory");
        this.error = new CommandLineUtils.StringStreamConsumer();
        this.output = new CommandLineUtils.StringStreamConsumer();
    }

    /**
     * Invokes the {@code protoc} compiler using the configuration specified at
     * construction.
     *
     * @return The exit status of {@code protoc}.
     * @throws CommandLineException
     */
    public int compile() throws CommandLineException {
        Commandline cl = new Commandline(executable);
        cl.addArguments(buildProtocCommand().toArray(new String[]{}));
        return CommandLineUtils.executeCommandLine(cl, null, output, error);
    }

    /**
     * Creates the command line arguments.
     * <p/>
     * This method has been made visible for testing only.
     *
     * @return A list consisting of the executable followed by any arguments.
     */
    ImmutableList<String> buildProtocCommand() {
        final List<String> command = newLinkedList();
        // add the executable

        // add --plugin if specified
        if (protocPluginNameAndPath != null) {
            command.add("--plugin=" + protocPluginNameAndPath);
        }

        for (File protoPathElement : protoPathElements) {
            command.add("--proto_path=" + protoPathElement);
        }

        // add output paths
        // add additional output paths requested
        boolean haveSpecifiedJavaOutputPath = false;
        for (String outputPath : additionalOutputPaths) {
            command.add("--" + outputPath);
            // detect whether we've been given a specific java_out as an additional output path
            if (!haveSpecifiedJavaOutputPath && outputPath.startsWith("java_out")) {
                haveSpecifiedJavaOutputPath = true;
            }
        }
        // as per initial behaviour and assuming we haven't over-ridden it in additionalOutputPaths,
        // --java_out is always provided
        if (!haveSpecifiedJavaOutputPath) {
            command.add("--java_out=" + javaOutputDirectory);
        }

        for (File protoFile : protofiles) {
            command.add(protoFile.toString());
        }
        return ImmutableList.copyOf(command);
    }

    /**
     * @return the output
     */
    public String getOutput() {
        return output.getOutput();
    }

    /**
     * @return the error
     */
    public String getError() {
        return error.getOutput();
    }

    /**
     * This class builds {@link Protoc} instances.
     *
     * @author gak@google.com (Gregory Kick)
     */
    static final class Builder {
        private final String executable;
        private final File javaOutputDirectory;
        private Set<String> additionalOutputs;
        private Set<File> protopathElements;
        private Set<File> protoFiles;
        private String protocPlugin;

        /**
         * Constructs a new builder. The two parameters are present as they are
         * required for all {@link Protoc} instances.
         *
         * @param executable          The path to the {@code protoc} executable.
         * @param protocPlugin        Plugin to be used in generating code from the proto files
         * @param additionalOutputs   Any additional outputs required
         * @param javaOutputDirectory The directory into which the java source files will be generated.
         * @throws NullPointerException If either of the arguments are {@code null}.  @throws IllegalArgumentException If the {@code javaOutputDirectory} is
         *                              not a directory.
         */
        public Builder(String executable, String protocPlugin, Set<String> additionalOutputs, File javaOutputDirectory) {
            this.protocPlugin = protocPlugin;
            this.additionalOutputs = additionalOutputs;
            this.executable = checkNotNull(executable, "executable");
            this.javaOutputDirectory = checkNotNull(javaOutputDirectory);
            checkArgument(javaOutputDirectory.isDirectory());
            this.protoFiles = newHashSet();
            this.protopathElements = newHashSet();
        }

        /**
         * Adds a proto file to be compiled. Proto files must be on the protopath
         * and this method will fail if a proto file is added without first adding a
         * parent directory to the protopath.
         *
         * @param protoFile
         * @return The builder.
         * @throws IllegalStateException If a proto file is added without first
         *                               adding a parent directory to the protopath.
         * @throws NullPointerException  If {@code protoFile} is {@code null}.
         */
        public Builder addProtoFile(File protoFile) {
            checkNotNull(protoFile);
            checkArgument(protoFile.isFile());
            checkArgument(protoFile.getName().endsWith(".proto"));
            checkProtoFileIsInProtopath(protoFile);
            protoFiles.add(protoFile);
            return this;
        }

        private void checkProtoFileIsInProtopath(File protoFile) {
            assert protoFile.isFile();
            checkState(checkProtoFileIsInProtopathHelper(protoFile.getParentFile()));
        }

        private boolean checkProtoFileIsInProtopathHelper(File directory) {
            assert directory.isDirectory();
            if (protopathElements.contains(directory)) {
                return true;
            } else {
                final File parentDirectory = directory.getParentFile();
                return (parentDirectory == null) ? false
                        : checkProtoFileIsInProtopathHelper(parentDirectory);
            }
        }

        /**
         * @see #addProtoFile(File)
         */
        public Builder addProtoFiles(Iterable<File> protoFiles) {
            for (File protoFile : protoFiles) {
                addProtoFile(protoFile);
            }
            return this;
        }

        /**
         * Adds the {@code protopathElement} to the protopath.
         *
         * @param protopathElement A directory to be searched for imported protocol
         *                         buffer definitions.
         * @return The builder.
         * @throws NullPointerException     If {@code protopathElement} is {@code null}.
         * @throws IllegalArgumentException If {@code protpathElement} is not a
         *                                  directory.
         */
        public Builder addProtoPathElement(File protopathElement) {
            checkNotNull(protopathElement);
            checkArgument(protopathElement.isDirectory());
            protopathElements.add(protopathElement);
            return this;
        }

        /**
         * @see #addProtoPathElement(File)
         */
        public Builder addProtoPathElements(Iterable<File> protopathElements) {
            for (File protopathElement : protopathElements) {
                addProtoPathElement(protopathElement);
            }
            return this;
        }

        /**
         * @return A configured {@link Protoc} instance.
         * @throws IllegalStateException If no proto files have been added.
         */
        public Protoc build() {
            checkState(!protoFiles.isEmpty());
            return new Protoc(executable, protocPlugin, ImmutableSet.copyOf(protopathElements),
                    ImmutableSet.copyOf(protoFiles), ImmutableSet.copyOf(additionalOutputs), javaOutputDirectory);
        }
    }
}
