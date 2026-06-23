# Maintainer: Xinfoo
pkgname=singcli
pkgver=0.1
pkgrel=1
pkgdesc='A lightweight command-line helper for sing-box'
arch=('any')
url='https://github.com/Xinfoo/singcli'
license=('MIT')
depends=('java-runtime>=17')
makedepends=('python' 'java-environment')
source=()
sha256sums=()
_builddir="$startdir/.makepkg-build/$pkgname-$pkgver"

prepare() {
    rm -rf "$_builddir"
    mkdir -p "$_builddir"

    cp -a "$startdir/build.py" "$_builddir/"
    cp -a "$startdir/src" "$_builddir/"
    cp -a "$startdir/scripts" "$_builddir/"
    cp -a "$startdir/README.md" "$_builddir/"
    cp -a "$startdir/LICENSE" "$_builddir/"
}

build() {
    cd "$_builddir"
    python build.py
}

package() {
    cd "$_builddir"

    install -Dm644 dist/singcli.jar "$pkgdir/opt/singcli/singcli.jar"
    install -Dm755 scripts/linux/singcli "$pkgdir/usr/bin/singcli"
    install -Dm644 README.md "$pkgdir/usr/share/doc/singcli/README.md"
    install -Dm644 LICENSE "$pkgdir/usr/share/licenses/$pkgname/LICENSE"
}
