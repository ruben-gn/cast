function toggleDescription(id) {
    const description = document.getElementById('desc-' + id);
    const fade = document.getElementById('fade-' + id);
    const button = document.getElementById('btn-' + id);

    const expanded = description.dataset.expanded === '1';

    if (expanded) {
        description.style.maxHeight = '6em';
        fade.style.opacity = '1';
        button.style.transform = 'rotate(0deg)';
        description.dataset.expanded = '0';
    } else {
        description.style.maxHeight = description.scrollHeight + 'px';
        fade.style.opacity = '0';
        button.style.transform = 'rotate(180deg)';
        description.dataset.expanded = '1';
    }
}

// Function to check if content actually needs a toggle
function initToggles() {
    document.querySelectorAll('[id^="desc-"]').forEach(desc => {
        const id = desc.id.replace('desc-', '');
        const fade = document.getElementById('fade-' + id);
        const button = document.getElementById('btn-' + id);
        
        // 6em is roughly 96px (16px * 6)
        if (desc.scrollHeight <= 100) { 
            if (fade) fade.style.display = 'none';
            if (button) button.style.display = 'none';
            desc.style.maxHeight = 'none';
        }
    });
}

// Run on load and after HTMX swaps
window.addEventListener('load', initToggles);
document.body.addEventListener('htmx:afterSwap', initToggles);