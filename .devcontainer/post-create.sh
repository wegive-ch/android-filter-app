set -e

sudo apt-get update

# Android tooling dependencies
sudo apt-get install -y \
    wget \
    unzip \
    git \
    curl \
    libc6 \
    libstdc++6 \
    zlib1g

# Android command-line tools
sudo mkdir -p /usr/local/android-sdk/cmdline-tools
cd /tmp

wget -q https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip

unzip -q commandlinetools-linux-13114758_latest.zip

sudo mv cmdline-tools /usr/local/android-sdk/cmdline-tools/latest

export ANDROID_HOME=/usr/local/android-sdk
export ANDROID_SDK_ROOT=/usr/local/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

yes | sdkmanager --licenses

sdkmanager \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0"

# Install OpenAI Codex CLI
npm install -g @openai/codex

echo "Setup complete."