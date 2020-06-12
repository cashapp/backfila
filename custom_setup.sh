echo "custom setup script"

# Using Ubuntu
curl -sL https://deb.nodesource.com/setup_14.x | bash -
apt-get install -y nodejs
npm install -g @misk/cli && miskweb ci-build -e