# Port maintenance

## Project and reference

- This is a Java 17 port of libFLAC 1.4.3. The upstream project is
  https://github.com/xiph/flac; use its `1.4.3` tag as the reference.
- Treat the pinned `src_upstream` Git submodule as authoritative and immutable.
  Initialize it with `git submodule update --init --recursive` when needed; it is
  not required for the Java build. Do not advance its pinned revision during
  routine port maintenance or consult other Java FLAC ports.
- Scope includes portable native/Ogg encoding and decoding, format utilities,
  metadata objects and all three metadata interfaces, standalone ReplayGain
  analysis/synthesis, and the two file examples in a separate module.
  C++ wrappers, command-line tools, grabbag, assembly/SIMD, integer-only builds,
  compatibility libraries and upstream development utilities are excluded.
- The base package is `io.github.raphimc.libflac`; ReplayGain has its own subpackage.
  Keep implementation helpers package-private, including the Ogg helpers.
  Ogg support has an optional JOrbis runtime dependency that consumers provide explicitly.

## Upstream alignment

- Preserve algorithms, processing stages, control flow, state transitions,
  callbacks, integer widths, unsigned operations, overflow and floating-point
  evaluation order. Read the whole C routine and its callers before changing it.
- Translate incomplete routines instead of adding compensating checks around
  shortcuts. Do not silently repair upstream behavior while refactoring the port.
- Retain semantic helper boundaries. Share or replace code only for a concrete
  Java representation, JDK operation or resource-handling purpose; brevity and
  matching output alone do not justify folding C routines together.
- Keep class, member, method and parameter names traceable to C. Preserve argument
  order except where receivers, typed results, array offsets or captured callback
  state require adaptation. CamelCase and ordinary accessors are appropriate.
- Follow C declaration order where practical, keeping public APIs and internal
  helpers grouped. Preserve field initialization and processing order. Put
  Java-specific nested types at the bottom; types mapping C structs retain their
  corresponding placement.
- Retain named constants and tables as `static final` fields with upstream
  spelling and semantic roles. Preserve arithmetic expressions and grouping;
  leave constant folding to the compiler. Use JDK constants directly instead of
  defining equivalent aliases. Do not substitute constants merely because their
  numeric values match.
- Preserve the wording and placement of relevant upstream comments, with only
  necessary Java adaptations or Javadoc formatting. Omit comments whose C-only
  operation has no Java counterpart. Do not invent unrelated implementation notes.

## Java conventions and API contracts

- Use Java 17, `Math`, explicit types and explicit imports. Follow `.editorconfig`,
  including four-space indentation and the 120-column line limit.
- Use byte arrays and the corresponding upstream packing operations for binary
  I/O. Do not introduce `ByteBuffer`, `DataInputStream`, `DataOutputStream` or
  generic serialization wrappers in the library, examples or tests.
- Do not override enum `toString`; expose descriptions through getters when
  needed. Omit redundant public constructors and `serialVersionUID` declarations.
- Map owning deletion to `AutoCloseable.close()`, retaining `finish()` and its
  upstream reset/reuse behavior. Let the JVM manage memory-only objects. Use
  resource scopes for native cleanup paths while preserving ownership and errors.
- Map `FILE*` operations to `RandomAccessFile`; retain filename, callback and
  standard-stream operations where provided upstream. Callback objects/captures
  replace opaque handles and `client_data`. Source and temporary output callbacks
  own separate stream state.
- Operations that always succeed on normal completion return `void`;
  binary success/failure or continue/abort results use `boolean`.
  Preserve meaningful counts, data, typed tags and multi-outcome statuses.
  Avoid adding public convenience wrappers.
- Use `IOException` for fatal I/O/processing failures and standard argument/state
  exceptions for invalid configuration or lifecycle use. Preserve normal status
  results and callback interruption. Update upstream state before propagation;
  retain callback exception contracts and the original failure during cleanup.
- Use mutable typed metadata, strings for text and byte arrays for binary data.
  Serialized text lengths count UTF-8 bytes. Use normal Java replacement, parsing
  and Math behavior; do not emulate C embedded-NUL conventions, libc parsing or
  rounding quirks, invalid references or invalid memory access.
  Use ordinary JVM array, file-position and allocation limits.
- Use `MessageDigest` for MD5 while retaining upstream PCM serialization.
  ReplayGain mutable state and random generators are instance-owned; preserve
  C's random sequence and call order within each independent instance.
- Use JOrbis's public framing APIs without modifying the dependency, accessing
  its internals through reflection or bypassing its framing behavior.
  Report material dependency limitations.

## Verification and release maintenance

- For behavioral changes, compare against an unchanged portable C build. Check
  results, states, callbacks, input consumption, metadata, exact PCM and encoded
  bytes where applicable. Explain representation/dependency differences; JUnit
  success and API coverage alone do not establish behavioral parity.
- The maintained JUnit suite is translated from upstream's unit suite. Preserve
  its applicable vectors and scenarios; keep additional exploratory/differential
  harnesses outside that suite. Record necessary test adaptations and omissions.
- Run checks appropriate to the change. Validate the build, examples and Javadoc
  with `./gradlew build javadoc :examples:javadoc` when relevant. Keep dependencies
  pinned and release coordinates/version information consistent.
- Keep current consumer guidance and material compatibility limitations in the
  README and API documentation. Avoid accumulating environment-specific reports
  or historical test results in the repository.
- Preserve original copyright/license headers, root `COPYING.*` texts, component
  attribution and license inclusion in distributions. Licensing is component-specific.
