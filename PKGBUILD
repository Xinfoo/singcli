# Maintainer: Xinfoo
pkgname=singcli
pkgver=1.2.4
pkgrel=1
pkgdesc='A lightweight command-line helper for sing-box'
arch=('x86_64')
url='https://github.com/Xinfoo/singcli'
license=('MIT')
depends=('glibc' 'sing-box' 'zlib')
# GraalVM Native Image must be available through GRAALVM_HOME, JAVA_HOME,
# PATH, or ~/.local/share/graalvm/current.
makedepends=('gcc' 'python' 'zlib')
options=('!debug')
_builddir="$startdir/.makepkg-build/$pkgname-$pkgver"

prepare() {
    rm -rf "$_builddir"
    mkdir -p "$_builddir"

    cp -a "$startdir/src" "$_builddir/"
    cp -a "$startdir/scripts" "$_builddir/"
    cp -a "$startdir/README.md" "$_builddir/"
    cp -a "$startdir/LICENSE" "$_builddir/"
}

build() {
    cd "$_builddir"
    python scripts/build/build-native.py
}

package() {
    cd "$_builddir"

    install -Dm755 dist/singcli "$pkgdir/usr/bin/singcli"
    install -Dm644 README.md "$pkgdir/usr/share/doc/singcli/README.md"
    install -Dm644 LICENSE "$pkgdir/usr/share/licenses/$pkgname/LICENSE"
}
