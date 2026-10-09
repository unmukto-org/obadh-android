# Source this file before building: source scripts/dev-env.sh
# Reuse the standard SDK and an existing compatible JDK; install nothing here.
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [ "$(uname -s)" = Darwin ]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
fi
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$HOME/.cargo/bin:$PATH"
