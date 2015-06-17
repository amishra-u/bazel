// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.rules.objc;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.Root;
import com.google.devtools.build.lib.analysis.AnalysisEnvironment;
import com.google.devtools.build.lib.analysis.AnalysisUtils;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.PathFragment;

/**
 * Factory class for generating artifacts which are used as intermediate output.
 */
// TODO(bazel-team): This should really be named DerivedArtifacts as it contains methods for
// final as well as intermediate artifacts.
final class IntermediateArtifacts {

  /**
   * Extension used on the temporary dsym bundle location. Must end in {@code .dSYM} for dsymutil
   * to generate a plist file.
   */
  static final String TMP_DSYM_BUNDLE_SUFFIX = ".temp.app.dSYM";

  private final AnalysisEnvironment analysisEnvironment;
  private final Root binDirectory;
  private final Label ownerLabel;
  private final String archiveFileNameSuffix;

  /**
   * Label to scope the output paths of generated artifacts, in addition to {@link #ownerLabel}.
   */
  private final Optional<Label> scopingLabel;

  IntermediateArtifacts(
      AnalysisEnvironment analysisEnvironment, Root binDirectory, Label ownerLabel,
      String archiveFileNameSuffix) {
    this.analysisEnvironment = Preconditions.checkNotNull(analysisEnvironment);
    this.binDirectory = Preconditions.checkNotNull(binDirectory);
    this.ownerLabel = Preconditions.checkNotNull(ownerLabel);
    this.scopingLabel = Optional.<Label>absent();
    this.archiveFileNameSuffix = Preconditions.checkNotNull(archiveFileNameSuffix);
  }

  IntermediateArtifacts(
      AnalysisEnvironment analysisEnvironment, Root binDirectory, Label ownerLabel,
      Label scopingLabel, String archiveFileNameSuffix) {
    this.analysisEnvironment = Preconditions.checkNotNull(analysisEnvironment);
    this.binDirectory = Preconditions.checkNotNull(binDirectory);
    this.ownerLabel = Preconditions.checkNotNull(ownerLabel);
    this.scopingLabel = Optional.of(Preconditions.checkNotNull(scopingLabel));
    this.archiveFileNameSuffix = Preconditions.checkNotNull(archiveFileNameSuffix);
  }

  /**
   * Returns a derived artifact in the bin directory obtained by appending some extension to the end
   * of the given {@link PathFragment}.
   */
  private Artifact appendExtension(PathFragment original, String extension) {
    return analysisEnvironment.getDerivedArtifact(
        FileSystemUtils.appendExtension(original, extension), binDirectory);
  }

  /**
   * Returns a derived artifact in the bin directory obtained by appending some extension to the end
   * of the {@link PathFragment} corresponding to the owner {@link Label}.
   */
  private Artifact appendExtension(String extension) {
    return appendExtension(labelScopedDir(), extension);
  }

  /**
   * The output of using {@code actoolzip} to run {@code actool} for a given bundle which is
   * merged under the {@code .app} or {@code .bundle} directory root.
   */
  public Artifact actoolzipOutput() {
    return appendExtension(".actool.zip");
  }

  /**
   * Output of the partial infoplist generated by {@code actool} when given the
   * {@code --output-partial-info-plist [path]} flag.
   */
  public Artifact actoolPartialInfoplist() {
    return appendExtension(".actool-PartialInfo.plist");
  }

  /**
   * The Info.plist file for a bundle which is comprised of more than one originating plist file.
   * This is not needed for a bundle which has no source Info.plist files, or only one Info.plist
   * file, since no merging occurs in that case.
   */
  public Artifact mergedInfoplist() {
    return appendExtension("-MergedInfo.plist");
  }

  /**
   * The .objlist file, which contains a list of paths of object files to archive  and is read by
   * libtool in the archive action.
   */
  public Artifact objList() {
    return appendExtension(".objlist");
  }

  /**
   * The artifact which is the binary (or library) which is comprised of one or more .a files linked
   * together. Compared to the artifact returned by {@link #unstrippedSingleArchitectureBinary},
   * this artifact is stripped of symbol table when --compilation_mode=opt is specified.
   */
  public Artifact strippedSingleArchitectureBinary() {
    return appendExtension("_bin");
  }

  /**
   * The artifact which is the binary (or library) which is comprised of one or more .a files linked
   * together. It also contains full debug symbol information, compared to the artifact returned
   * by {@link #strippedSingleArchitectureBinary}. This artifact will serve as input for the symbol
   * strip action and is only created when --compilation_mode=opt is specified.
   */
  public Artifact unstrippedSingleArchitectureBinary() {
    return appendExtension("_bin_unstripped");
  }

  /**
   * Lipo binary generated by combining one or more linked binaries. This binary is the one included
   * in generated bundles and invoked as entry point to the application.
   */
  public Artifact combinedArchitectureBinary() {
    return appendExtension("_lipobin");
  }

  /**
   * The {@code .a} file which contains all the compiled sources for a rule.
   */
  public Artifact archive() {
    PathFragment labelPath = labelScopedDir();
    PathFragment rootRelative = labelPath
        .getParentDirectory()
        .getRelative(String.format("lib%s%s.a", labelPath.getBaseName(), archiveFileNameSuffix));
    return analysisEnvironment.getDerivedArtifact(rootRelative, binDirectory);
  }

  /**
   * The debug symbol bundle file which contains debug symbols generated by dsymutil.
   */
  public Artifact dsymBundle() {
    return appendExtension(TMP_DSYM_BUNDLE_SUFFIX);
  }

  /**
   * Returns a unique directory scoped by {@code ownerLabel} and {@code scopingLabel}. Normally
   * when {@code scopingLabel} is absent, the returned directory is just a path fragment
   * containing the package and the name of the ownerLabel. If scopingLabel is present, the
   * returned directory is scoped by scopingLabel first. For example, if ownerLabel is
   * //a/b:c and scopingLabel is //d/e:f, the returned directory will be: d/e/a/b/c/f/.
   */
  private PathFragment labelScopedDir() {
    if (scopingLabel.isPresent()) {
      return AnalysisUtils.getUniqueDirectory(scopingLabel.get(), ownerLabel.toPathFragment());
    } else {
      return ownerLabel.toPathFragment();
    }
  }

  private PathFragment inUniqueObjsDir(Artifact source, String extension) {
    PathFragment labelPath = labelScopedDir();
    PathFragment uniqueDir = labelPath.getParentDirectory().getRelative(new PathFragment("_objs"))
        .getRelative(labelPath.getBaseName());
    PathFragment sourceFile = uniqueDir.getRelative(source.getRootRelativePath());
    return FileSystemUtils.replaceExtension(sourceFile, extension);
  }

  /**
   * The artifact for the .o file that should be generated when compiling the {@code source}
   * artifact.
   */
  public Artifact objFile(Artifact source) {
     return analysisEnvironment.getDerivedArtifact(inUniqueObjsDir(source, ".o"), binDirectory);
  }

  /**
   * The swift module produced by compiling the {@code source} artifact.
   */
  public Artifact swiftModuleFile(Artifact source) {
    return analysisEnvironment.getDerivedArtifact(inUniqueObjsDir(source, ".partial_swiftmodule"),
        binDirectory);
  }

  /**
   * Integrated swift module for this target.
   */
  public Artifact swiftModule() {
    return appendExtension(".swiftmodule");
  }

  /**
   * Integrated swift header for this target.
   */
  public Artifact swiftHeader() {
    return appendExtension("-Swift.h");
  }

  /**
   * The artifact for the .gcno file that should be generated when compiling the {@code source}
   * artifact.
   */
  public Artifact gcnoFile(Artifact source) {
     return analysisEnvironment.getDerivedArtifact(inUniqueObjsDir(source, ".gcno"), binDirectory);
  }

  /**
   * Returns the artifact corresponding to the pbxproj control file, which specifies the information
   * required to generate the Xcode project file.
   */
  public Artifact pbxprojControlArtifact() {
    return appendExtension(".xcodeproj-control");
  }

  /**
   * The artifact which contains the zipped-up results of compiling the storyboard. This is merged
   * into the final bundle under the {@code .app} or {@code .bundle} directory root.
   */
  public Artifact compiledStoryboardZip(Artifact input) {
    return appendExtension("/" + BundleableFile.flatBundlePath(input.getExecPath()) + ".zip");
  }

  /**
   * Returns the artifact which is the output of building an entire xcdatamodel[d] made of artifacts
   * specified by a single rule.
   *
   * @param containerDir the containing *.xcdatamodeld or *.xcdatamodel directory
   * @return the artifact for the zipped up compilation results.
   */
  public Artifact compiledMomZipArtifact(PathFragment containerDir) {
    return appendExtension(
        "/" + FileSystemUtils.replaceExtension(containerDir, ".zip").getBaseName());
  }

  /**
   * Returns the compiled (i.e. converted to binary plist format) artifact corresponding to the
   * given {@code .strings} file.
   */
  public Artifact convertedStringsFile(Artifact originalFile) {
    return appendExtension(originalFile.getExecPath(), ".binary");
  }

  /**
   * Returns the artifact corresponding to the zipped-up compiled form of the given {@code .xib}
   * file.
   */
  public Artifact compiledXibFileZip(Artifact originalFile) {
    return appendExtension(
        "/" + FileSystemUtils.replaceExtension(originalFile.getExecPath(), ".nib.zip"));
  }

  /**
   * Returns the artifact which is the output of running swift-stdlib-tool and copying resulting
   * dylibs.
   */
  public Artifact swiftFrameworksFileZip() {
    return appendExtension(".swiftstdlib.zip");
  }

  /**
   * Debug symbol plist generated for a linked binary.
   */
  public Artifact dsymPlist() {
    return appendExtension(".app.dSYM/Contents/Info.plist");
  }

  /**
   * Debug symbol file generated for a linked binary.
   */
  public Artifact dsymSymbol() {
    return appendExtension(
        String.format(".app.dSYM/Contents/Resources/DWARF/%s_bin", ownerLabel.getName()));
  }

  /**
   * Breakpad debug symbol representation.
   */
  public Artifact breakpadSym() {
    return appendExtension(".breakpad");
  }

  /**
   * Breakpad debug symbol representation for a specific architecture.
   */
  public Artifact breakpadSym(String arch) {
    return appendExtension(String.format("_%s.breakpad", arch));
  }

  /**
   * Shell script that launches the binary.
   */
  public Artifact runnerScript() {
    return appendExtension("_runner.sh");
  }
}
