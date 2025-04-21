import sys
from PyQt5.QtWidgets import (QApplication, QMainWindow, QTabWidget, QVBoxLayout, 
                            QWidget, QToolBar, QAction, QLineEdit)
from PyQt5.QtWebEngineWidgets import QWebEngineView
from PyQt5.QtCore import QUrl
from PyQt5.QtGui import QIcon

DEFAULT_URL = "https://www.kadaza.com/"

class Browser(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Mega Browser")
        self.resize(1000, 700)

        self.tabs = QTabWidget()
        self.tabs.setTabsClosable(True)
        self.tabs.tabCloseRequested.connect(self.close_tab)
        self.setCentralWidget(self.tabs)

        # Navigation toolbar
        navtb = QToolBar("Navigation")
        self.addToolBar(navtb)

        back_btn = QAction("⬅️", self)
        back_btn.triggered.connect(lambda: self.tabs.currentWidget().back())
        navtb.addAction(back_btn)

        forward_btn = QAction("➡️", self)
        forward_btn.triggered.connect(lambda: self.tabs.currentWidget().forward())
        navtb.addAction(forward_btn)

        reload_btn = QAction("🔄", self)
        reload_btn.triggered.connect(lambda: self.tabs.currentWidget().reload())
        navtb.addAction(reload_btn)

        # URL Bar
        self.urlbar = QLineEdit()
        self.urlbar.returnPressed.connect(self.navigate_to_url)
        navtb.addWidget(self.urlbar)

        new_tab_btn = QAction("➕ New Tab", self)
        new_tab_btn.triggered.connect(self.new_tab)
        navtb.addAction(new_tab_btn)

        self.add_new_tab(QUrl(DEFAULT_URL), "New Tab")

    def add_new_tab(self, qurl, label):
        browser = QWebEngineView()
        browser.setUrl(qurl)
        browser.loadFinished.connect(lambda _, browser=browser: 
            self.tabs.setTabText(self.tabs.indexOf(browser), browser.page().title()))
        browser.urlChanged.connect(lambda url, browser=browser: 
            self.update_urlbar(url, browser))
        
        i = self.tabs.addTab(browser, label)
        self.tabs.setCurrentIndex(i)
        return browser

    def new_tab(self):
        self.add_new_tab(QUrl(DEFAULT_URL), "New Tab")

    def close_tab(self, index):
        if self.tabs.count() > 1:
            self.tabs.removeTab(index)
        else:
            self.close()

    def navigate_to_url(self):
        url = self.urlbar.text()
        if not url.startswith(('http://', 'https://')):
            url = 'http://' + url
        self.tabs.currentWidget().setUrl(QUrl(url))

    def update_urlbar(self, url, browser=None):
        if browser != self.tabs.currentWidget():
            return
        self.urlbar.setText(url.toString())
        self.urlbar.setCursorPosition(0)

if __name__ == '__main__':
    app = QApplication(sys.argv)
    window = Browser()
    window.show()
    sys.exit(app.exec_())