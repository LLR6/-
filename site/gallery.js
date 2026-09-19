import PhotoSwipeLightbox from './vendor/photoswipe/photoswipe-lightbox.esm.js';

const lightbox = new PhotoSwipeLightbox({
  gallery: '#cgGallery',
  children: 'a',
  pswpModule: () => import('./vendor/photoswipe/photoswipe.esm.js'),
  bgOpacity: 0.96,
  wheelToZoom: true,
  paddingFn: () => ({ top: 28, bottom: 28, left: 28, right: 28 })
});

lightbox.on('uiRegister', () => {
  lightbox.pswp.ui.registerElement({
    name: 'lr-caption',
    order: 9,
    isButton: false,
    appendTo: 'root',
    html: '',
    onInit: (el, pswp) => {
      const update = () => {
        const curr = pswp.currSlide?.data?.element;
        const title = curr?.querySelector('b')?.textContent || 'LR//NEO';
        const detail = curr?.querySelector('small')?.textContent || '';
        el.innerHTML = '<b>' + title + '</b><span>' + detail + '</span>';
      };
      pswp.on('change', update);
      update();
    }
  });
});

lightbox.init();
